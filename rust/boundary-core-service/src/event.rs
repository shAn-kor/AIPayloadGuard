use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use boundary_core::{Decision, DetectorFinding, GuardInput, GuardOutput, Severity};
use chrono::{DateTime, Utc};
use serde::Serialize;
use sha2::{Digest, Sha256};
use tokio::fs::OpenOptions;
use tokio::io::AsyncWriteExt;

#[derive(Debug, Clone, Serialize)]
pub struct GuardEvent {
    pub event_id: String,
    pub request_id: String,
    pub payload_type: String,
    pub decision: String,
    pub risk_score: u8,
    pub severity: String,
    pub high_risk: bool,
    pub policy_revision: String,
    pub content_hash: String,
    pub redacted_summary: String,
    pub redaction_count: usize,
    pub violation_count: usize,
    pub violations: Vec<GuardEventViolation>,
    pub metadata: BTreeMap<String, String>,
    pub core_latency_ms: u64,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize)]
pub struct GuardEventViolation {
    pub policy_id: String,
    pub violation_type: String,
    pub severity: String,
    pub message: String,
    pub start_offset: usize,
    pub end_offset: usize,
    pub detector: String,
}

impl GuardEvent {
    pub fn from_check(input: &GuardInput, output: &GuardOutput, core_latency_ms: u64) -> Self {
        let severity = highest_severity(&output.findings);
        let event_id = format!("evt-{}", input.request_id);

        Self {
            event_id,
            request_id: input.request_id.clone(),
            payload_type: payload_type_name(&input.payload_kind).to_string(),
            decision: decision_name(output.decision).to_string(),
            risk_score: output.risk_score,
            severity: severity.to_string(),
            high_risk: output.decision == Decision::Block || output.risk_score >= 70,
            policy_revision: output.policy_revision.clone(),
            content_hash: content_hash(&input.content),
            redacted_summary: summarize_redacted_content(&output.redaction.redacted_content),
            redaction_count: output.redaction.spans.len(),
            violation_count: output.findings.len(),
            violations: output.findings.iter().map(violation_from_finding).collect(),
            metadata: BTreeMap::new(),
            core_latency_ms,
            created_at: Utc::now(),
        }
    }
}

#[async_trait]
pub trait GuardEventPublisher: Send + Sync + 'static {
    async fn publish(&self, event: GuardEvent) -> Result<(), GuardEventPublishError>;
}

#[derive(Debug, Clone)]
pub struct GuardEventPublishError {
    message: String,
}

impl GuardEventPublishError {
    pub fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl std::fmt::Display for GuardEventPublishError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter.write_str(&self.message)
    }
}

impl std::error::Error for GuardEventPublishError {}

impl From<std::io::Error> for GuardEventPublishError {
    fn from(error: std::io::Error) -> Self {
        Self::new(error.to_string())
    }
}

impl From<serde_json::Error> for GuardEventPublishError {
    fn from(error: serde_json::Error) -> Self {
        Self::new(error.to_string())
    }
}

#[derive(Debug, Default)]
pub struct NoopGuardEventPublisher;

#[async_trait]
impl GuardEventPublisher for NoopGuardEventPublisher {
    async fn publish(&self, _event: GuardEvent) -> Result<(), GuardEventPublishError> {
        Ok(())
    }
}

#[derive(Debug, Clone)]
pub struct JsonlGuardEventPublisher {
    path: PathBuf,
}

impl JsonlGuardEventPublisher {
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self { path: path.into() }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }
}

#[async_trait]
impl GuardEventPublisher for JsonlGuardEventPublisher {
    async fn publish(&self, event: GuardEvent) -> Result<(), GuardEventPublishError> {
        if let Some(parent) = self.path.parent() {
            tokio::fs::create_dir_all(parent).await?;
        }

        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.path)
            .await?;

        let line = serde_json::to_string(&event)?;
        file.write_all(line.as_bytes()).await?;
        file.write_all(b"\n").await?;
        file.flush().await?;

        Ok(())
    }
}

#[derive(Debug, Default)]
pub struct InMemoryGuardEventPublisher {
    events: Mutex<Vec<GuardEvent>>,
}

impl InMemoryGuardEventPublisher {
    pub fn events(&self) -> Vec<GuardEvent> {
        self.events.lock().expect("guard event lock").clone()
    }
}

#[async_trait]
impl GuardEventPublisher for InMemoryGuardEventPublisher {
    async fn publish(&self, event: GuardEvent) -> Result<(), GuardEventPublishError> {
        self.events.lock().expect("guard event lock").push(event);
        Ok(())
    }
}

#[derive(Debug)]
pub struct FailingGuardEventPublisher;

#[async_trait]
impl GuardEventPublisher for FailingGuardEventPublisher {
    async fn publish(&self, _event: GuardEvent) -> Result<(), GuardEventPublishError> {
        Err(GuardEventPublishError::new("simulated publisher failure"))
    }
}

pub type SharedGuardEventPublisher = Arc<dyn GuardEventPublisher>;

pub fn noop_publisher() -> SharedGuardEventPublisher {
    Arc::new(NoopGuardEventPublisher)
}

fn content_hash(content: &str) -> String {
    let digest = Sha256::digest(content.as_bytes());
    format!("sha256:{digest:x}")
}

fn summarize_redacted_content(redacted_content: &str) -> String {
    const MAX_SUMMARY_CHARS: usize = 240;

    redacted_content.chars().take(MAX_SUMMARY_CHARS).collect()
}

fn highest_severity(findings: &[DetectorFinding]) -> &'static str {
    if findings.iter().any(|finding| finding.severity == Severity::Critical) {
        "CRITICAL"
    } else if findings.iter().any(|finding| finding.severity == Severity::High) {
        "HIGH"
    } else if findings.iter().any(|finding| finding.severity == Severity::Medium) {
        "MEDIUM"
    } else if findings.iter().any(|finding| finding.severity == Severity::Low) {
        "LOW"
    } else {
        "NONE"
    }
}

fn violation_from_finding(finding: &DetectorFinding) -> GuardEventViolation {
    GuardEventViolation {
        policy_id: finding.policy_id.clone(),
        violation_type: format!("{:?}", finding.kind),
        severity: format!("{:?}", finding.severity).to_uppercase(),
        message: finding.message.clone(),
        start_offset: finding.start_offset,
        end_offset: finding.end_offset,
        detector: finding.detector.clone(),
    }
}

fn decision_name(decision: Decision) -> &'static str {
    match decision {
        Decision::Allow => "ALLOW",
        Decision::Redact => "REDACT",
        Decision::Block => "BLOCK",
    }
}

fn payload_type_name(payload_kind: &boundary_core::PayloadKind) -> &'static str {
    match payload_kind {
        boundary_core::PayloadKind::Text => "TEXT",
        boundary_core::PayloadKind::Prompt => "PROMPT",
        boundary_core::PayloadKind::Response => "RESPONSE",
        boundary_core::PayloadKind::DataEgress => "DATA_EGRESS",
    }
}

#[cfg(test)]
mod tests {
    use boundary_core::{GuardCore, PayloadKind};

    use super::*;

    fn input(content: &str) -> GuardInput {
        GuardInput {
            request_id: "req-event-1".to_string(),
            payload_kind: PayloadKind::Prompt,
            content: content.to_string(),
            policy_revision: "policy-v1".to_string(),
        }
    }

    #[test]
    fn guard_event_does_not_store_raw_payload() {
        let input = input("secret user@example.com");
        let output = GuardCore::new().check(input.clone());
        let event = GuardEvent::from_check(&input, &output, 3);
        let serialized = serde_json::to_string(&event).expect("serialize event");

        assert!(!serialized.contains("secret user@example.com"));
        assert!(!serialized.contains("user@example.com"));
        assert!(serialized.contains("sha256:"));
        assert!(serialized.contains("[REDACTED:PII]"));
    }

    #[tokio::test]
    async fn in_memory_publisher_records_events() {
        let publisher = InMemoryGuardEventPublisher::default();
        let input = input("hello");
        let output = GuardCore::new().check(input.clone());
        let event = GuardEvent::from_check(&input, &output, 1);

        publisher.publish(event).await.expect("publish event");

        assert_eq!(publisher.events().len(), 1);
    }
}
