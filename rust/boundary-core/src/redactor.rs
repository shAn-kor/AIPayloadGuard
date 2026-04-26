use crate::detector::{DetectorFinding, FindingKind};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Redaction {
    pub redacted: bool,
    pub redacted_content: String,
    pub spans: Vec<RedactionSpan>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RedactionSpan {
    pub start_offset: usize,
    pub end_offset: usize,
    pub replacement: String,
    pub kind: FindingKind,
}

pub fn redact(original: &str, findings: &[DetectorFinding]) -> Redaction {
    let mut spans = findings
        .iter()
        .filter(|finding| finding.is_redactable())
        .filter(|finding| finding.normalized_source == "original" || finding.normalized_source == "unicode")
        .map(|finding| RedactionSpan {
            start_offset: finding.start_offset,
            end_offset: finding.end_offset,
            replacement: replacement_for(finding.kind),
            kind: finding.kind,
        })
        .collect::<Vec<_>>();

    spans.sort_by_key(|span| span.start_offset);
    spans = remove_overlapping_spans(spans);

    let redacted_content = apply_spans(original, &spans);

    Redaction {
        redacted: !spans.is_empty(),
        redacted_content,
        spans,
    }
}

fn replacement_for(kind: FindingKind) -> String {
    match kind {
        FindingKind::Pii => "[REDACTED:PII]".to_string(),
        FindingKind::Secret => "[REDACTED:SECRET]".to_string(),
        FindingKind::PromptInjection => "[REDACTED]".to_string(),
    }
}

fn remove_overlapping_spans(spans: Vec<RedactionSpan>) -> Vec<RedactionSpan> {
    let mut result: Vec<RedactionSpan> = Vec::new();

    for span in spans {
        if result
            .last()
            .is_some_and(|previous| span.start_offset < previous.end_offset)
        {
            continue;
        }
        result.push(span);
    }

    result
}

fn apply_spans(original: &str, spans: &[RedactionSpan]) -> String {
    if spans.is_empty() {
        return original.to_string();
    }

    let mut output = String::new();
    let mut cursor = 0;

    for span in spans {
        if span.start_offset > original.len() || span.end_offset > original.len() || span.start_offset >= span.end_offset {
            continue;
        }

        output.push_str(&original[cursor..span.start_offset]);
        output.push_str(&span.replacement);
        cursor = span.end_offset;
    }

    output.push_str(&original[cursor..]);
    output
}

#[cfg(test)]
mod tests {
    use crate::detector::{DetectorFinding, FindingKind, Severity};

    use super::redact;

    #[test]
    fn redacts_original_pii_span() {
        let findings = vec![DetectorFinding {
            policy_id: "pii.email".to_string(),
            kind: FindingKind::Pii,
            severity: Severity::Medium,
            start_offset: 6,
            end_offset: 22,
            detector: "pii".to_string(),
            normalized_source: "original".to_string(),
            message: "Email address detected".to_string(),
        }];

        let result = redact("email user@example.com", &findings);

        assert!(result.redacted);
        assert_eq!(result.redacted_content, "email [REDACTED:PII]");
    }
}
