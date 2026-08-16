package com.expensemanagement.saga;

import com.expensemanagement.event.EventPublisher;
import com.expensemanagement.event.EventType;
import com.expensemanagement.grpc.AnomalyScoreQuery;
import com.expensemanagement.grpc.AnomalyScoreResult;
import com.expensemanagement.grpc.GrpcAnomalyScoringClient;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.ExpenseAnomalyAssessment;
import com.expensemanagement.repository.ExpenseAnomalyAssessmentRepository;
import com.expensemanagement.repository.ExpenseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * First step of the expense-submission saga: score the submission for
 * anomalies and record the result.
 *
 * Has no meaningful compensate() -- scoring is a read of external state plus
 * a write of a brand-new assessment row that nothing downstream depends on
 * existing. If a later saga step fails, there's nothing here to undo; the
 * assessment stays as an accurate record of what was scored and when.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnomalyScoringSagaStep implements SagaStep<ExpenseSagaContext> {

    private final ExpenseRepository expenseRepository;
    private final ExpenseAnomalyAssessmentRepository assessmentRepository;
    private final GrpcAnomalyScoringClient scoringClient;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "ANOMALY_SCORING";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(ExpenseSagaContext context) {
        Expense expense = expenseRepository.findById(context.getExpenseId())
                .orElseThrow(() -> new SagaStepException("Expense not found: " + context.getExpenseId()));

        AnomalyScoreQuery query = buildQuery(expense);
        AnomalyScoreResult result = scoringClient.score(query);

        ExpenseAnomalyAssessment assessment = ExpenseAnomalyAssessment.builder()
                .expense(expense)
                .anomalyScore(result.available() ? result.anomalyScore() : 0.0)
                .isAnomalous(result.available() && result.isAnomalous())
                .percentileInReference(result.percentileInReference())
                .featuresJson(toJson(result.features()))
                .modelVersion(result.modelVersion())
                .scoringUnavailable(!result.available())
                .build();
        assessmentRepository.save(assessment);

        context.setAnomalyScore(result.anomalyScore());
        // Fail safe: if the scoring service was unreachable, flag for human
        // review rather than assuming the submission is clean.
        context.setFlaggedForReview(!result.available() || result.isAnomalous());

        eventPublisher.publish(EventType.EXPENSE_SCORED, "Expense", expense.getId(), context.getOrgId(),
                Map.of(
                        "anomalyScore", assessment.getAnomalyScore(),
                        "isAnomalous", assessment.getIsAnomalous(),
                        "scoringUnavailable", assessment.isScoringUnavailable()
                ));

        if (context.isFlaggedForReview()) {
            eventPublisher.publish(EventType.EXPENSE_FLAGGED_FOR_REVIEW, "Expense", expense.getId(), context.getOrgId(),
                    Map.of("reason", result.available() ? "anomaly_score_above_threshold" : "scoring_service_unavailable"));
        }

        log.info("Scored expense {} for org {}: score={} anomalous={} flagged={}",
                expense.getId(), context.getOrgId(), assessment.getAnomalyScore(), assessment.getIsAnomalous(), context.isFlaggedForReview());
    }

    @Override
    public void compensate(ExpenseSagaContext context) {
        log.debug("No compensation required for {} on expense {} (read-only assessment)", name(), context.getExpenseId());
    }

    private AnomalyScoreQuery buildQuery(Expense expense) {
        Long userId = expense.getUser().getId();

        List<AnomalyScoreQuery.RecentSubmissionRef> recent = expenseRepository
                .findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(e -> !e.getId().equals(expense.getId()))
                .map(e -> new AnomalyScoreQuery.RecentSubmissionRef(
                        e.getAmount(), e.getCategory().name(), vendorOf(e), e.getExpenseDate(),
                        e.getSubmittedAt() != null ? e.getSubmittedAt() : e.getCreatedAt()))
                .toList();

        List<Double> categoryHistory = expenseRepository
                .findTop20ByUserIdAndCategoryOrderByExpenseDateDesc(userId, expense.getCategory()).stream()
                .filter(e -> !e.getId().equals(expense.getId()))
                .map(e -> e.getAmount().doubleValue())
                .toList();

        return new AnomalyScoreQuery(
                expense.getOrganization().getId(),
                userId,
                expense.getCategory().name(),
                expense.getAmount(),
                vendorOf(expense),
                expense.getExpenseDate(),
                expense.getSubmittedAt() != null ? expense.getSubmittedAt() : expense.getCreatedAt(),
                recent,
                categoryHistory
        );
    }

    /** The current schema has no dedicated vendor field on Expense; the
     * description is the closest available proxy. A follow-up migration
     * that adds a first-class `vendor` column would let this go away. */
    private String vendorOf(Expense expense) {
        return expense.getDescription();
    }

    private String toJson(Map<String, Double> features) {
        try {
            return objectMapper.writeValueAsString(features);
        } catch (Exception e) {
            return "{}";
        }
    }
}
