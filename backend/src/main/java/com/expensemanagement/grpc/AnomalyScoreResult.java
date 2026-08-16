package com.expensemanagement.grpc;

import java.util.Map;

/**
 * Plain-Java response from the anomaly-scoring service. `available == false`
 * means the call did not complete (circuit open, timeout, retries
 * exhausted) -- the saga step treats that as "flag for human review" rather
 * than either blocking the whole submission or silently treating an unknown
 * expense as safe.
 */
public record AnomalyScoreResult(
        boolean available,
        double anomalyScore,
        boolean isAnomalous,
        double percentileInReference,
        Map<String, Double> features,
        String modelVersion
) {
    public static AnomalyScoreResult unavailable() {
        return new AnomalyScoreResult(false, 0.0, false, 0.0, Map.of(), "unavailable");
    }
}
