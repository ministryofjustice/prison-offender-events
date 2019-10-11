CREATE TABLE POLL_AUDIT
(
    poll_name          VARCHAR(64) PRIMARY KEY,
    lastrun_start      TIMESTAMP NOT NULL,
    lastrun_end        TIMESTAMP NOT NULL,
    latest_record_time TIMESTAMP NOT NULL,
    number_of_records  INT       NOT NULL
);

GRANT SELECT, INSERT, UPDATE, DELETE ON POLL_AUDIT TO offender_events;
