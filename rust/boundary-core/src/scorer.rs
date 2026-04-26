use crate::detector::{DetectorFinding, FindingKind, Severity};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub struct RiskScore(u8);

impl RiskScore {
    pub fn new(value: u8) -> Self {
        Self(value.min(100))
    }

    pub fn value(self) -> u8 {
        self.0
    }
}

pub fn score(findings: &[DetectorFinding]) -> RiskScore {
    let mut total: u16 = 0;

    for finding in findings {
        total += severity_weight(finding.severity);
        total += kind_weight(finding.kind);
    }

    RiskScore::new(total.min(100) as u8)
}

fn severity_weight(severity: Severity) -> u16 {
    match severity {
        Severity::Low => 10,
        Severity::Medium => 25,
        Severity::High => 50,
        Severity::Critical => 90,
    }
}

fn kind_weight(kind: FindingKind) -> u16 {
    match kind {
        FindingKind::PromptInjection => 10,
        FindingKind::Pii => 5,
        FindingKind::Secret => 20,
    }
}

#[cfg(test)]
mod tests {
    use crate::detector::{DetectorFinding, FindingKind, Severity};

    use super::score;

    #[test]
    fn critical_prompt_injection_scores_high() {
        let findings = vec![DetectorFinding {
            policy_id: "prompt_injection.instruction_override".to_string(),
            kind: FindingKind::PromptInjection,
            severity: Severity::Critical,
            start_offset: 0,
            end_offset: 10,
            detector: "prompt_injection".to_string(),
            normalized_source: "original".to_string(),
            message: "Prompt injection pattern detected".to_string(),
        }];

        assert!(score(&findings).value() >= 90);
    }
}
