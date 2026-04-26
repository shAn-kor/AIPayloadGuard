pub mod guard {
    pub mod v1 {
        tonic::include_proto!("guard.v1");
    }
}

use std::{net::SocketAddr, time::Instant};

use boundary_core::{Decision, DetectorFinding, FindingKind, GuardCore, GuardInput, PayloadKind, Redaction, Severity};
use guard::v1::guard_core_service_server::{GuardCoreService, GuardCoreServiceServer};
use guard::v1::{
    CoreHealthCheckRequest, CoreHealthCheckResult, DecisionType, GuardCheckRequest, GuardCheckResult,
    PayloadType, RedactionResult, RedactionSpan, Severity as ProtoSeverity, ViolationEvidence,
    ViolationType,
};
use tonic::{transport::Server, Request, Response, Status};

pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");
pub const DEFAULT_BIND_ADDR: &str = "127.0.0.1:50051";

#[derive(Debug, Default)]
pub struct BoundaryGuardService {
    core: GuardCore,
}

impl BoundaryGuardService {
    pub fn new(core: GuardCore) -> Self {
        Self { core }
    }
}

#[tonic::async_trait]
impl GuardCoreService for BoundaryGuardService {
    async fn check(
        &self,
        request: Request<GuardCheckRequest>,
    ) -> Result<Response<GuardCheckResult>, Status> {
        let started_at = Instant::now();
        let proto_request = request.into_inner();
        let input = guard_input_from_proto(&proto_request)?;
        let output = self.core.check(input);
        let core_latency_ms = started_at.elapsed().as_millis().min(u64::MAX as u128) as u64;

        Ok(Response::new(guard_result_to_proto(output, core_latency_ms)))
    }

    async fn health(
        &self,
        request: Request<CoreHealthCheckRequest>,
    ) -> Result<Response<CoreHealthCheckResult>, Status> {
        let request = request.into_inner();

        Ok(Response::new(CoreHealthCheckResult {
            request_id: request.request_id,
            ready: true,
            core_version: CORE_VERSION.to_string(),
            loaded_policy_revision: "builtin-mvp".to_string(),
            enabled_detectors: vec![
                "prompt_injection".to_string(),
                "pii".to_string(),
                "secret".to_string(),
            ],
        }))
    }
}

fn guard_input_from_proto(request: &GuardCheckRequest) -> Result<GuardInput, Status> {
    if request.request_id.trim().is_empty() {
        return Err(Status::invalid_argument("request_id is required"));
    }

    Ok(GuardInput {
        request_id: request.request_id.clone(),
        payload_kind: payload_kind_from_proto(request.payload_type())?,
        content: request.content.clone(),
        policy_revision: normalize_policy_revision(&request.policy_revision),
    })
}

fn normalize_policy_revision(policy_revision: &str) -> String {
    if policy_revision.trim().is_empty() {
        "builtin-mvp".to_string()
    } else {
        policy_revision.to_string()
    }
}

fn payload_kind_from_proto(payload_type: PayloadType) -> Result<PayloadKind, Status> {
    match payload_type {
        PayloadType::Text => Ok(PayloadKind::Text),
        PayloadType::Prompt => Ok(PayloadKind::Prompt),
        PayloadType::Response => Ok(PayloadKind::Response),
        PayloadType::DataEgress => Ok(PayloadKind::DataEgress),
        PayloadType::Unspecified => Err(Status::invalid_argument("payload_type is required")),
    }
}

fn guard_result_to_proto(output: boundary_core::GuardOutput, core_latency_ms: u64) -> GuardCheckResult {
    GuardCheckResult {
        request_id: output.request_id,
        decision: decision_to_proto(output.decision).into(),
        risk_score: output.risk_score as u32,
        violations: output.findings.into_iter().map(finding_to_proto).collect(),
        redaction_result: Some(redaction_to_proto(output.redaction)),
        policy_revision: output.policy_revision,
        core_latency_ms,
    }
}

fn decision_to_proto(decision: Decision) -> DecisionType {
    match decision {
        Decision::Allow => DecisionType::Allow,
        Decision::Redact => DecisionType::Redact,
        Decision::Block => DecisionType::Block,
    }
}

fn finding_to_proto(finding: DetectorFinding) -> ViolationEvidence {
    ViolationEvidence {
        policy_id: finding.policy_id,
        violation_type: finding_kind_to_proto(finding.kind).into(),
        severity: severity_to_proto(finding.severity).into(),
        message: finding.message,
        start_offset: finding.start_offset as u32,
        end_offset: finding.end_offset as u32,
        detector: finding.detector,
    }
}

fn finding_kind_to_proto(kind: FindingKind) -> ViolationType {
    match kind {
        FindingKind::PromptInjection => ViolationType::PromptInjection,
        FindingKind::Pii => ViolationType::Pii,
        FindingKind::Secret => ViolationType::Secret,
    }
}

fn severity_to_proto(severity: Severity) -> ProtoSeverity {
    match severity {
        Severity::Low => ProtoSeverity::Low,
        Severity::Medium => ProtoSeverity::Medium,
        Severity::High => ProtoSeverity::High,
        Severity::Critical => ProtoSeverity::Critical,
    }
}

fn redaction_to_proto(redaction: Redaction) -> RedactionResult {
    RedactionResult {
        redacted: redaction.redacted,
        redacted_content: redaction.redacted_content,
        redaction_count: redaction.spans.len() as u32,
        spans: redaction
            .spans
            .into_iter()
            .map(|span| RedactionSpan {
                start_offset: span.start_offset as u32,
                end_offset: span.end_offset as u32,
                replacement: span.replacement,
                violation_type: finding_kind_to_proto(span.kind).into(),
            })
            .collect(),
    }
}

pub async fn run_server(bind_addr: SocketAddr, core: GuardCore) -> Result<(), Box<dyn std::error::Error>> {
    let service = BoundaryGuardService::new(core);

    Server::builder()
        .add_service(GuardCoreServiceServer::new(service))
        .serve(bind_addr)
        .await?;

    Ok(())
}

