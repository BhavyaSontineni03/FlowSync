package com.expensemanagement.grpc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Plain-Java request to the anomaly-scoring service, kept separate from the
 * generated protobuf type so the rest of the codebase (the saga step, its
 * tests) never has to import generated gRPC classes directly.
 */
public record AnomalyScoreQuery(
        Long orgId,
        Long userId,
        String category,
        BigDecimal amount,
        String vendor,
        LocalDate expenseDate,
        LocalDateTime submittedAt,
        List<RecentSubmissionRef> recentSubmissions,
        List<Double> userCategoryHistory
) {
    public record RecentSubmissionRef(
            BigDecimal amount,
            String category,
            String vendor,
            LocalDate expenseDate,
            LocalDateTime submittedAt
    ) {}
}
