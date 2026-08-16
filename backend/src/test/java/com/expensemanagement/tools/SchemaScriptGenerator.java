package com.expensemanagement.tools;

import com.expensemanagement.event.DomainEvent;
import com.expensemanagement.model.*;
import com.expensemanagement.saga.SagaExecution;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

/**
 * Dev utility: emits the PostgreSQL DDL implied by the current JPA entity
 * mappings, purely from metadata (no live database connection required).
 * Wrote the Flyway V1 baseline migration this way; when a future entity
 * change needs a new migration, rerun this, diff its output against the
 * last migration, and hand-write the delta as V<n>__description.sql.
 * Not wired into the build or referenced by any test.
 *
 * Run with: mvn test-compile, then
 *   java -cp target/classes:target/test-classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
 *     com.expensemanagement.tools.SchemaScriptGenerator
 */
public class SchemaScriptGenerator {

    public static void main(String[] args) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                // Must match Spring Boot's actual defaults (HibernateJpaAutoConfiguration)
                // exactly, or the generated DDL's column names (createdAt vs
                // created_at) will not match what the running application uses.
                .applySetting("hibernate.implicit_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy")
                .applySetting("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target", "target/generated-schema.sql")
                .applySetting("jakarta.persistence.schema-generation.create-source", "metadata")
                .build();

        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(Organization.class)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(Project.class)
                    .addAnnotatedClass(ProjectAssignment.class)
                    .addAnnotatedClass(Expense.class)
                    .addAnnotatedClass(ExpenseAnomalyAssessment.class)
                    .addAnnotatedClass(Approval.class)
                    .addAnnotatedClass(LeaveRequest.class)
                    .addAnnotatedClass(LeaveBalance.class)
                    .addAnnotatedClass(Timesheet.class)
                    .addAnnotatedClass(Payroll.class)
                    .addAnnotatedClass(PaymentLedgerEntry.class)
                    .addAnnotatedClass(OrgBudget.class)
                    .addAnnotatedClass(Notification.class)
                    .addAnnotatedClass(ActivityLog.class)
                    .addAnnotatedClass(AdminRequest.class)
                    .addAnnotatedClass(SagaExecution.class)
                    .addAnnotatedClass(DomainEvent.class)
                    .buildMetadata();

            // Building the SessionFactory triggers Hibernate's standard schema
            // management coordinator, which honors the script-generation
            // settings above and writes the DDL file without ever opening a
            // JDBC connection (source = metadata, target = script only).
            metadata.buildSessionFactory().close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
