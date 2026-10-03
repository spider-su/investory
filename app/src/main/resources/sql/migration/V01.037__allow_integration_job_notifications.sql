SET search_path TO investory, public;

ALTER TABLE investory.notification_event
    DROP CONSTRAINT ck_notification_event_type;

ALTER TABLE investory.notification_event
    ADD CONSTRAINT ck_notification_event_type CHECK (event_type IN (
        'DAILY_DIGEST', 'THRESHOLD_ALERT', 'SYSTEM_AUDIT_ERROR',
        'IMPORT_FAILED_OR_PARTIAL', 'INTEGRATION_JOB_ALERT',
        'PLAN_BECAME_UNSUSTAINABLE'));
