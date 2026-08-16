-- Baseline schema for the Org-Isolated Financial Operations Platform.
--
-- This is the entire schema as of the first production release, generated
-- from the JPA entity mappings (see SchemaScriptGenerator in the test
-- sources for how to regenerate this DDL when drafting a new migration).
-- Hibernate no longer owns schema evolution at runtime: application.yml sets
-- ddl-auto to validate, so every future schema change ships as its own
-- V<n>__description.sql file here instead of relying on auto-generated
-- ALTER statements against a live production database.
--
-- Existing DBs (Hibernate-created volumes): spring.flyway.baseline-on-migrate
-- + baseline-version=1 records this version as already applied without
-- re-executing the CREATE statements below, so user data is preserved.
-- Fresh empty databases run this script on first migrate.
-- Uniqueness aligned with entities: projects (organization_id, code),
-- leave_balances (user_id, leave_year).

-- ============================================================
-- Core tenancy and identity
-- ============================================================

create table organizations (
    id              bigserial not null,
    name            varchar(255) not null unique,
    subdomain       varchar(255) not null unique,
    address         varchar(255) not null,
    contact_email   varchar(255) not null,
    contact_phone   varchar(255) not null,
    created_at      timestamp(6) not null,
    updated_at      timestamp(6),
    primary key (id)
);

create table users (
    id              bigserial not null,
    organization_id bigint not null,
    manager_id      bigint,
    hr_id           bigint,
    email           varchar(255) not null unique,
    password        varchar(255) not null,
    first_name      varchar(255) not null,
    last_name       varchar(255) not null,
    phone_number    varchar(20),
    address         varchar(500),
    role            varchar(255) not null check (role in ('EMPLOYEE','MANAGER','ADMIN','FINANCE','HR')),
    enabled         boolean not null,
    is_on_bench     boolean not null,
    monthly_salary  numeric(10,2),
    created_at      timestamp(6) not null,
    updated_at      timestamp(6),
    primary key (id)
);

-- ============================================================
-- Projects and staffing
-- ============================================================

create table projects (
    id              bigserial not null,
    organization_id bigint not null,
    manager_id      bigint,
    code            varchar(50) not null,
    name            varchar(200) not null,
    description     varchar(1000),
    status          varchar(255) not null check (status in ('ACTIVE','INACTIVE','COMPLETED','ON_HOLD')),
    start_date      date not null,
    end_date        date,
    created_at      timestamp(6) not null,
    updated_at      timestamp(6),
    primary key (id),
    constraint idx_project_org_code unique (organization_id, code)
);

create table project_assignments (
    id              bigserial not null,
    project_id      bigint not null,
    user_id         bigint not null,
    role            varchar(100),
    assigned_date   date not null,
    unassigned_date date,
    is_active       boolean not null,
    created_at      timestamp(6) not null,
    updated_at      timestamp(6),
    primary key (id)
);

-- ============================================================
-- Expenses, approvals, anomaly scoring, and payment
-- ============================================================

create table expenses (
    id                  bigserial not null,
    organization_id     bigint not null,
    user_id             bigint not null,
    amount              numeric(10,2) not null,
    expense_date        date not null,
    description         varchar(500) not null,
    category            varchar(255) not null check (category in ('TRAVEL','MEALS','ACCOMMODATION','TRANSPORTATION','OFFICE_SUPPLIES','SOFTWARE','TRAINING','ENTERTAINMENT','UTILITIES','OTHER')),
    status              varchar(255) not null check (status in ('PENDING','SUBMITTED','APPROVED','REJECTED','PAID')),
    receipt_path        varchar(500),
    receipt_url         varchar(500),
    ocr_extracted_data  varchar(1000),
    notes               varchar(1000),
    submitted_at        timestamp(6),
    created_at          timestamp(6) not null,
    updated_at          timestamp(6),
    primary key (id)
);

create table approvals (
    id           bigserial not null,
    expense_id   bigint not null,
    approver_id  bigint not null,
    status       varchar(255) not null check (status in ('PENDING','APPROVED','REJECTED')),
    comments     varchar(1000),
    created_at   timestamp(6) not null,
    primary key (id)
);

create table expense_anomaly_assessments (
    id                      bigserial not null,
    expense_id              bigint not null unique,
    anomaly_score           float(53) not null,
    is_anomalous            boolean not null,
    percentile_in_reference float(53),
    scoring_unavailable     boolean not null,
    model_version           varchar(60) not null,
    features_json           TEXT,
    scored_at               timestamp(6) not null,
    primary key (id)
);

create table org_budgets (
    id                bigserial not null,
    organization_id   bigint not null,
    period_year       integer not null,
    period_month      integer not null,
    allocated_amount  numeric(14,2) not null,
    consumed_amount   numeric(14,2) not null,
    version           bigint,
    primary key (id),
    constraint uk_org_budget_period unique (organization_id, period_year, period_month)
);

create table payment_ledger_entries (
    id                bigserial not null,
    expense_id        bigint not null,
    org_budget_id     bigint not null,
    amount            numeric(12,2) not null,
    status            varchar(20) not null check (status in ('COMPLETED','REVERSED')),
    reversal_reason   varchar(255),
    created_at        timestamp(6) not null,
    updated_at        timestamp(6),
    primary key (id)
);

-- ============================================================
-- Leave, timesheets, and payroll
-- ============================================================

create table leave_balances (
    id                          bigserial not null,
    organization_id             bigint not null,
    user_id                     bigint not null,
    leave_year                  integer not null,
    paid_leave_allocated        integer not null,
    paid_leave_used             integer not null,
    sick_leave_allocated        integer not null,
    sick_leave_used             integer not null,
    personal_leave_allocated    integer not null,
    personal_leave_used         integer not null,
    unpaid_leave_allocated      integer not null,
    unpaid_leave_used           integer not null,
    created_at                  timestamp(6) not null,
    updated_at                  timestamp(6),
    primary key (id),
    constraint idx_leave_balance_user_year unique (user_id, leave_year)
);

create table leave_requests (
    id                  bigserial not null,
    organization_id     bigint not null,
    user_id             bigint not null,
    approved_by         bigint,
    leave_type          varchar(255) not null check (leave_type in ('VACATION','SICK_LEAVE','PERSONAL_LEAVE','UNPAID_LEAVE','MATERNITY_LEAVE','PATERNITY_LEAVE','BEREAVEMENT','OTHER')),
    status              varchar(255) not null check (status in ('PENDING','APPROVED','REJECTED','CANCELLED')),
    start_date          date not null,
    end_date            date not null,
    number_of_days      integer not null,
    is_paid             boolean not null,
    paid_days           integer not null,
    unpaid_days         integer not null,
    reason              varchar(1000),
    approval_comments   varchar(1000),
    approved_at         timestamp(6),
    created_at          timestamp(6) not null,
    updated_at          timestamp(6),
    primary key (id)
);

create table timesheets (
    id                  bigserial not null,
    organization_id     bigint not null,
    user_id             bigint not null,
    project_id          bigint,
    leave_request_id    bigint,
    approved_by         bigint,
    date                date not null,
    hours               float(53) not null,
    project_code        varchar(50) not null,
    entry_type          varchar(255) not null check (entry_type in ('WORK','LEAVE','HOLIDAY')),
    leave_type          varchar(255) check (leave_type in ('VACATION','SICK_LEAVE','PERSONAL_LEAVE','UNPAID_LEAVE','MATERNITY_LEAVE','PATERNITY_LEAVE','BEREAVEMENT','OTHER')),
    is_paid_leave       boolean,
    status              varchar(255) not null check (status in ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
    description         varchar(1000),
    approval_comments   varchar(1000),
    submitted_at        timestamp(6),
    approved_at         timestamp(6),
    created_at          timestamp(6) not null,
    updated_at          timestamp(6),
    primary key (id)
);

create table payrolls (
    id                      bigserial not null,
    organization_id         bigint not null,
    user_id                 bigint not null,
    processed_by            bigint,
    period_year             integer not null,
    period_month            integer not null,
    base_salary             numeric(10,2) not null,
    days_worked             integer not null,
    total_days_in_month     integer not null,
    paid_leaves_used        integer not null,
    unpaid_leaves_used      integer not null,
    deductions              numeric(10,2) not null,
    net_salary              numeric(10,2) not null,
    status                  varchar(255) not null check (status in ('DRAFT','PROCESSED','PAID','CANCELLED')),
    notes                   varchar(1000),
    processed_at            timestamp(6),
    created_at              timestamp(6) not null,
    updated_at              timestamp(6),
    primary key (id),
    constraint idx_payroll_user_period unique (user_id, period_month, period_year)
);

-- ============================================================
-- Notifications, activity audit trail, and admin approval queue
-- ============================================================

create table notifications (
    id                    bigserial not null,
    user_id               bigint not null,
    type                  varchar(255) not null check (type in ('EXPENSE_SUBMITTED','EXPENSE_APPROVED','EXPENSE_REJECTED','APPROVAL_REQUESTED','EXPENSE_PAID','PAYROLL_PAID','SYSTEM_ANNOUNCEMENT')),
    title                 varchar(200) not null,
    message               varchar(1000) not null,
    is_read               boolean not null,
    related_entity_id     bigint,
    related_entity_type   varchar(50),
    created_at            timestamp(6) not null,
    primary key (id)
);

create table activity_logs (
    id              bigserial not null,
    organization_id bigint not null,
    user_id         bigint,
    activity_type   varchar(255) not null check (activity_type in ('EXPENSE_CREATED','EXPENSE_UPDATED','EXPENSE_DELETED','EXPENSE_SUBMITTED','EXPENSE_APPROVED','EXPENSE_REJECTED','EXPENSE_PAID','USER_CREATED','USER_UPDATED','USER_DELETED','ORGANIZATION_CREATED','ORGANIZATION_UPDATED','RECEIPT_UPLOADED','OCR_PROCESSED','LEAVE_REQUEST_CREATED','LEAVE_REQUEST_UPDATED','LEAVE_REQUEST_APPROVED','LEAVE_REQUEST_REJECTED','LEAVE_REQUEST_CANCELLED','TIMESHEET_CREATED','TIMESHEET_SUBMITTED','TIMESHEET_APPROVED','TIMESHEET_REJECTED','PAYROLL_CALCULATED','PAYROLL_PROCESSED','PROJECT_CREATED','PROJECT_UPDATED','PROJECT_DELETED','PROJECT_EMPLOYEE_ASSIGNED','PROJECT_EMPLOYEE_UNASSIGNED','ADMIN_REQUEST_CREATED','ADMIN_REQUEST_APPROVED','ADMIN_REQUEST_REJECTED','USER_LOGGED_IN')),
    entity_type     varchar(50),
    entity_id       bigint,
    description     varchar(500) not null,
    metadata        varchar(2000),
    created_at      timestamp(6) not null,
    primary key (id)
);

create table admin_requests (
    id              bigserial not null,
    organization_id bigint not null,
    requested_by    bigint not null,
    approved_by     bigint,
    type            varchar(255) not null check (type in ('USER_CREATE','USER_UPDATE','USER_DISABLE','PROJECT_CREATE','PROJECT_UPDATE','PROJECT_DELETE','PROJECT_ASSIGN','PROJECT_UNASSIGN','PROFILE_UPDATE')),
    status          varchar(255) not null check (status in ('PENDING','APPROVED','REJECTED')),
    payload_json    TEXT not null,
    description     varchar(500),
    comments        varchar(1000),
    created_at      timestamp(6) not null,
    updated_at      timestamp(6),
    primary key (id)
);

-- ============================================================
-- Event sourcing and saga orchestration
-- ============================================================

create table domain_events (
    id              bigserial not null,
    org_id          bigint not null,
    aggregate_type  varchar(60) not null,
    aggregate_id    bigint not null,
    event_type      varchar(40) not null check (event_type in ('EXPENSE_SUBMITTED','EXPENSE_SCORED','EXPENSE_FLAGGED_FOR_REVIEW','EXPENSE_APPROVED','EXPENSE_REJECTED','EXPENSE_PAYMENT_STARTED','EXPENSE_PAID','EXPENSE_PAYMENT_COMPENSATED')),
    payload         TEXT not null,
    published       boolean not null,
    occurred_at     timestamp(6) not null,
    primary key (id)
);

create table saga_executions (
    id              bigserial not null,
    org_id          bigint not null,
    expense_id      bigint not null,
    saga_type       varchar(60) not null,
    status          varchar(20) not null check (status in ('IN_PROGRESS','COMPLETED','COMPENSATING','COMPENSATED','FAILED')),
    current_step    varchar(60),
    last_error      TEXT,
    started_at      timestamp(6) not null,
    updated_at      timestamp(6),
    primary key (id)
);

-- ============================================================
-- Indexes
-- ============================================================

create index idx_activity_org on activity_logs (organization_id);
create index idx_activity_user on activity_logs (user_id);
create index idx_activity_entity on activity_logs (entity_type, entity_id);
create index idx_activity_created on activity_logs (created_at);

create index idx_approval_expense on approvals (expense_id);
create index idx_approval_approver on approvals (approver_id);

create index idx_domain_event_aggregate on domain_events (aggregate_type, aggregate_id);
create index idx_domain_event_org on domain_events (org_id);
create index idx_domain_event_published on domain_events (published);

create index idx_expense_user on expenses (user_id);
create index idx_expense_org on expenses (organization_id);
create index idx_expense_status on expenses (status);
create index idx_expense_date on expenses (expense_date);
create index idx_expense_org_date on expenses (organization_id, expense_date);
create index idx_expense_org_status on expenses (organization_id, status);
create index idx_expense_category on expenses (category);

create index idx_leave_balance_org on leave_balances (organization_id);

create index idx_leave_user on leave_requests (user_id);
create index idx_leave_org on leave_requests (organization_id);
create index idx_leave_status on leave_requests (status);
create index idx_leave_dates on leave_requests (start_date, end_date);
create index idx_leave_org_status on leave_requests (organization_id, status);

create index idx_notification_user on notifications (user_id);
create index idx_notification_read on notifications (is_read);
create index idx_notification_created on notifications (created_at);

create index idx_ledger_expense on payment_ledger_entries (expense_id);

create index idx_payroll_user on payrolls (user_id);
create index idx_payroll_period on payrolls (period_month, period_year);
create index idx_payroll_status on payrolls (status);
create index idx_payroll_org on payrolls (organization_id);

create index idx_assignment_user on project_assignments (user_id);
create index idx_assignment_project on project_assignments (project_id);
create index idx_assignment_user_project on project_assignments (user_id, project_id);
create index idx_assignment_active on project_assignments (is_active);

create index idx_project_code on projects (code);
create index idx_project_org on projects (organization_id);
create index idx_project_status on projects (status);

create index idx_saga_expense on saga_executions (expense_id);
create index idx_saga_status on saga_executions (status);

create index idx_timesheet_user on timesheets (user_id);
create index idx_timesheet_project on timesheets (project_id);
create index idx_timesheet_date on timesheets (date);
create index idx_timesheet_status on timesheets (status);
create index idx_timesheet_user_date on timesheets (user_id, date);
create index idx_timesheet_org on timesheets (organization_id);
create index idx_timesheet_entry_type on timesheets (entry_type);

create index idx_user_email on users (email);
create index idx_user_org on users (organization_id);

-- ============================================================
-- Foreign keys (added last so table creation order above doesn't matter)
-- ============================================================

alter table users add constraint fk_user_organization foreign key (organization_id) references organizations;
alter table users add constraint fk_user_manager foreign key (manager_id) references users;
alter table users add constraint fk_user_hr foreign key (hr_id) references users;

alter table projects add constraint fk_project_organization foreign key (organization_id) references organizations;
alter table projects add constraint fk_project_manager foreign key (manager_id) references users;

alter table project_assignments add constraint fk_assignment_project foreign key (project_id) references projects;
alter table project_assignments add constraint fk_assignment_user foreign key (user_id) references users;

alter table expenses add constraint fk_expense_organization foreign key (organization_id) references organizations;
alter table expenses add constraint fk_expense_user foreign key (user_id) references users;

alter table approvals add constraint fk_approval_expense foreign key (expense_id) references expenses;
alter table approvals add constraint fk_approval_approver foreign key (approver_id) references users;

alter table expense_anomaly_assessments add constraint fk_assessment_expense foreign key (expense_id) references expenses;

alter table org_budgets add constraint fk_budget_organization foreign key (organization_id) references organizations;

alter table payment_ledger_entries add constraint fk_ledger_expense foreign key (expense_id) references expenses;
alter table payment_ledger_entries add constraint fk_ledger_budget foreign key (org_budget_id) references org_budgets;

alter table leave_balances add constraint fk_leave_balance_organization foreign key (organization_id) references organizations;
alter table leave_balances add constraint fk_leave_balance_user foreign key (user_id) references users;

alter table leave_requests add constraint fk_leave_request_organization foreign key (organization_id) references organizations;
alter table leave_requests add constraint fk_leave_request_user foreign key (user_id) references users;
alter table leave_requests add constraint fk_leave_request_approver foreign key (approved_by) references users;

alter table timesheets add constraint fk_timesheet_organization foreign key (organization_id) references organizations;
alter table timesheets add constraint fk_timesheet_user foreign key (user_id) references users;
alter table timesheets add constraint fk_timesheet_project foreign key (project_id) references projects;
alter table timesheets add constraint fk_timesheet_leave_request foreign key (leave_request_id) references leave_requests;
alter table timesheets add constraint fk_timesheet_approver foreign key (approved_by) references users;

alter table payrolls add constraint fk_payroll_organization foreign key (organization_id) references organizations;
alter table payrolls add constraint fk_payroll_user foreign key (user_id) references users;
alter table payrolls add constraint fk_payroll_processor foreign key (processed_by) references users;

alter table notifications add constraint fk_notification_user foreign key (user_id) references users;

alter table activity_logs add constraint fk_activity_organization foreign key (organization_id) references organizations;
alter table activity_logs add constraint fk_activity_user foreign key (user_id) references users;

alter table admin_requests add constraint fk_admin_request_organization foreign key (organization_id) references organizations;
alter table admin_requests add constraint fk_admin_request_requester foreign key (requested_by) references users;
alter table admin_requests add constraint fk_admin_request_approver foreign key (approved_by) references users;
