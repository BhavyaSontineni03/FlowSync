package com.expensemanagement.grpc;

import com.expensemanagement.grpc.anomaly.AnomalyScoringServiceGrpc;
import com.expensemanagement.grpc.anomaly.RecentSubmission;
import com.expensemanagement.grpc.anomaly.ScoreExpenseRequest;
import com.expensemanagement.grpc.anomaly.ScoreExpenseResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Client to the Python anomaly-scoring service's gRPC endpoint, guarded by a
 * circuit breaker and a bounded retry (see resilience4j.* in
 * application.yml for the actual thresholds).
 *
 * Why a circuit breaker matters here specifically: the scoring call sits
 * directly in the expense-submission path. Without one, a slow or down
 * scoring service would make every expense submission slow or fail right
 * along with it -- a dependency that is genuinely optional (submissions can
 * proceed and be flagged for manual review instead) taking down a path that
 * shouldn't depend on it. Once the failure/slow-call rate crosses the
 * configured threshold, the breaker opens and every call fails fast into
 * `scoreFallback` for the wait-duration-in-open-state window, instead of
 * every request queuing up behind a dependency that's already struggling.
 */
@Component
@Slf4j
public class GrpcAnomalyScoringClient {

    private final AnomalyScoringServiceGrpc.AnomalyScoringServiceBlockingStub blockingStub;
    private final long deadlineMs;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public GrpcAnomalyScoringClient(ManagedChannel anomalyScoringChannel,
                                     @Value("${app.anomaly-scoring.deadline-ms}") long deadlineMs) {
        this.blockingStub = AnomalyScoringServiceGrpc.newBlockingStub(anomalyScoringChannel);
        this.deadlineMs = deadlineMs;
    }

    @CircuitBreaker(name = "anomalyScoring", fallbackMethod = "scoreFallback")
    @Retry(name = "anomalyScoring")
    public AnomalyScoreResult score(AnomalyScoreQuery query) {
        ScoreExpenseRequest.Builder builder = ScoreExpenseRequest.newBuilder()
                .setOrgId(String.valueOf(query.orgId()))
                .setUserId(String.valueOf(query.userId()))
                .setCategory(query.category())
                .setAmount(query.amount().doubleValue())
                .setVendor(query.vendor())
                .setExpenseDate(query.expenseDate().format(DATE_FMT))
                .setSubmittedAt(query.submittedAt().format(DATETIME_FMT));

        for (var recent : query.recentSubmissions()) {
            builder.addRecentSubmissions(RecentSubmission.newBuilder()
                    .setAmount(recent.amount().doubleValue())
                    .setCategory(recent.category())
                    .setVendor(recent.vendor())
                    .setExpenseDate(recent.expenseDate().format(DATE_FMT))
                    .setSubmittedAt(recent.submittedAt().format(DATETIME_FMT))
                    .build());
        }
        query.userCategoryHistory().forEach(builder::addUserCategoryHistory);

        ScoreExpenseResponse response = blockingStub
                .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .scoreExpense(builder.build());

        return new AnomalyScoreResult(
                true,
                response.getAnomalyScore(),
                response.getIsAnomalous(),
                response.getPercentileInReference(),
                response.getFeaturesMap(),
                response.getModelVersion()
        );
    }

    /**
     * Resilience4j fallback: same signature as `score` plus the triggering
     * exception. Called when the circuit is open, the deadline is exceeded,
     * or retries are exhausted. Returning a well-formed "unavailable" result
     * (rather than throwing further) lets the saga step decide what a
     * degraded scoring service means for the business flow -- here, that
     * means flagging the expense for manual review instead of blocking
     * submission entirely.
     */
    private AnomalyScoreResult scoreFallback(AnomalyScoreQuery query, Throwable t) {
        log.warn("Anomaly scoring unavailable for org {} (amount={}): {}. Falling back to manual review.",
                query.orgId(), query.amount(), t.toString());
        return AnomalyScoreResult.unavailable();
    }
}
