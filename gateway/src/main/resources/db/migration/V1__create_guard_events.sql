CREATE TABLE guard_events (
    event_id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    payload_type VARCHAR(32) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    risk_score INTEGER NOT NULL,
    severity VARCHAR(32) NOT NULL,
    high_risk BOOLEAN NOT NULL,
    policy_revision VARCHAR(128) NOT NULL,
    provider_type VARCHAR(64),
    provider_name VARCHAR(128),
    provider_model VARCHAR(128),
    provider_endpoint VARCHAR(512),
    principal_id VARCHAR(128),
    tenant_id VARCHAR(128),
    project_id VARCHAR(128),
    environment VARCHAR(64),
    content_hash CHAR(64) NOT NULL,
    redacted_summary TEXT,
    redaction_count INTEGER NOT NULL,
    core_latency_ms BIGINT NOT NULL,
    violations_json TEXT NOT NULL
);

CREATE INDEX idx_guard_events_request_id ON guard_events (request_id);
CREATE INDEX idx_guard_events_occurred_at ON guard_events (occurred_at);
CREATE INDEX idx_guard_events_decision ON guard_events (decision);
CREATE INDEX idx_guard_events_high_risk ON guard_events (high_risk);
