use std::fs;

use boundary_core::{Decision, GuardCore, GuardInput, PayloadKind};
use serde::Deserialize;

#[derive(Debug, Deserialize)]
struct RegressionCase {
    name: String,
    payload_kind: PayloadKindFixture,
    content: String,
    expected_decision: DecisionFixture,
    #[serde(default)]
    expected_policy_ids: Vec<String>,
    #[serde(default)]
    forbidden_policy_ids: Vec<String>,
    #[serde(default)]
    expected_normalized_sources: Vec<String>,
    #[serde(default)]
    expected_redaction_markers: Vec<String>,
}

#[derive(Debug, Deserialize)]
enum PayloadKindFixture {
    Text,
    Prompt,
    Response,
    DataEgress,
}

#[derive(Debug, Deserialize)]
enum DecisionFixture {
    Allow,
    Redact,
    Block,
}

impl From<PayloadKindFixture> for PayloadKind {
    fn from(value: PayloadKindFixture) -> Self {
        match value {
            PayloadKindFixture::Text => PayloadKind::Text,
            PayloadKindFixture::Prompt => PayloadKind::Prompt,
            PayloadKindFixture::Response => PayloadKind::Response,
            PayloadKindFixture::DataEgress => PayloadKind::DataEgress,
        }
    }
}

impl From<DecisionFixture> for Decision {
    fn from(value: DecisionFixture) -> Self {
        match value {
            DecisionFixture::Allow => Decision::Allow,
            DecisionFixture::Redact => Decision::Redact,
            DecisionFixture::Block => Decision::Block,
        }
    }
}

#[test]
fn text_payload_regression_fixtures_match_expected_decisions() {
    let cases = load_regression_cases();
    let core = GuardCore::new();

    for case in cases {
        let case_name = case.name.clone();
        let expected_decision = Decision::from(case.expected_decision);
        let output = core.check(GuardInput {
            request_id: format!("regression-{case_name}"),
            payload_kind: PayloadKind::from(case.payload_kind),
            content: case.content,
            policy_revision: "regression-policy-v1".to_string(),
        });

        assert_eq!(
            output.decision, expected_decision,
            "case {case_name} returned unexpected decision with findings {:?}",
            output.findings
        );

        for policy_id in &case.expected_policy_ids {
            assert!(
                output.findings.iter().any(|finding| finding.policy_id == *policy_id),
                "case {case_name} did not include expected policy id {policy_id}; findings: {:?}",
                output.findings
            );
        }

        for policy_id in &case.forbidden_policy_ids {
            assert!(
                output.findings.iter().all(|finding| finding.policy_id != *policy_id),
                "case {case_name} included forbidden policy id {policy_id}; findings: {:?}",
                output.findings
            );
        }

        for source in &case.expected_normalized_sources {
            assert!(
                output.findings.iter().any(|finding| finding.normalized_source == *source),
                "case {case_name} did not include expected normalized source {source}; findings: {:?}",
                output.findings
            );
        }

        for marker in &case.expected_redaction_markers {
            assert!(
                output.redaction.redacted_content.contains(marker),
                "case {case_name} redaction did not include marker {marker}; redaction: {:?}",
                output.redaction
            );
        }
    }
}

fn load_regression_cases() -> Vec<RegressionCase> {
    let fixture_path = concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/tests/fixtures/regression/text_payload_cases.json"
    );
    let fixture = fs::read_to_string(fixture_path).expect("regression fixture should be readable");
    serde_json::from_str(&fixture).expect("regression fixture should be valid json")
}
