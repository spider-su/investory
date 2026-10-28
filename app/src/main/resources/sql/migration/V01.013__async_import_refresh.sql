ALTER TABLE investory.import_history ADD CONSTRAINT uq_import_history_id_portfolio UNIQUE(id,portfolio_id);
CREATE TABLE investory.import_refresh_job (
  batch_id BIGINT PRIMARY KEY,
  portfolio_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
  affected_account_ids JSONB NOT NULL CHECK(jsonb_typeof(affected_account_ids)='array'),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  error VARCHAR(256),
  CONSTRAINT fk_import_refresh_batch_portfolio FOREIGN KEY(batch_id,portfolio_id) REFERENCES investory.import_history(id,portfolio_id)
);
CREATE INDEX idx_import_refresh_job_pending ON investory.import_refresh_job(batch_id) WHERE status IN ('PENDING','RUNNING');
ALTER TABLE investory.notification_event DROP CONSTRAINT ck_notification_event_type;
ALTER TABLE investory.notification_event ADD CONSTRAINT ck_notification_event_type CHECK(event_type IN ('DAILY_DIGEST','THRESHOLD_ALERT','SYSTEM_AUDIT_ERROR','IMPORT_FAILED_OR_PARTIAL','IMPORT_REFRESH_FINISHED','INTEGRATION_JOB_ALERT','PLAN_BECAME_UNSUSTAINABLE'));

ALTER TABLE investory.notification_event DROP CONSTRAINT ck_notification_event_severity;
ALTER TABLE investory.notification_event ADD CONSTRAINT ck_notification_event_severity CHECK(severity IN ('INFO','WARNING','ERROR','CRITICAL'));
