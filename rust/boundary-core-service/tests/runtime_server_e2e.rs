use std::sync::Arc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use boundary_core::GuardCore;
use boundary_core_service::event::JsonlGuardEventPublisher;
use boundary_core_service::guard::v1::guard_core_service_client::GuardCoreServiceClient;
use boundary_core_service::guard::v1::{DecisionType, GuardCheckRequest, PayloadType};
use boundary_core_service::run_server_with_event_publisher;
use tokio::time::sleep;

#[tokio::test]
async fn rust_runtime_server_check_writes_jsonl_event_without_raw_payload() {
    let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("bind random local port");
    let bind_addr = listener.local_addr().expect("local addr");
    drop(listener);

    let event_log = std::env::temp_dir().join(format!(
        "boundary-guard-runtime-e2e-{}-{}.jsonl",
        std::process::id(),
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("system time")
            .as_nanos()
    ));
    let _ = tokio::fs::remove_file(&event_log).await;

    let publisher = Arc::new(JsonlGuardEventPublisher::new(&event_log));
    let server_task = tokio::spawn(async move {
        run_server_with_event_publisher(bind_addr, GuardCore::new(), publisher)
            .await
            .expect("runtime server should run");
    });

    let endpoint = format!("http://{bind_addr}");
    let mut client = connect_with_retry(endpoint).await;
    let raw_payload = "email user@example.com";

    let response = client
        .check(GuardCheckRequest {
            request_id: "runtime-e2e-1".to_string(),
            payload_type: PayloadType::Prompt.into(),
            content: raw_payload.to_string(),
            provider_metadata: None,
            principal_context: None,
            policy_revision: "policy-v1".to_string(),
            metadata: Default::default(),
        })
        .await
        .expect("check should succeed")
        .into_inner();

    assert_eq!(response.decision, DecisionType::Redact as i32);
    assert!(response.redaction_result.expect("redaction result").redacted);

    let content = read_event_log_with_retry(&event_log).await;
    assert!(content.contains("\"request_id\":\"runtime-e2e-1\""));
    assert!(content.contains("\"decision\":\"REDACT\""));
    assert!(content.contains("\"content_hash\":\"sha256:"));
    assert!(content.contains("[REDACTED:PII]"));
    assert!(!content.contains(raw_payload));
    assert!(!content.contains("user@example.com"));

    server_task.abort();
    let _ = tokio::fs::remove_file(&event_log).await;
}

async fn connect_with_retry(endpoint: String) -> GuardCoreServiceClient<tonic::transport::Channel> {
    let mut last_error = None;
    for _ in 0..50 {
        match GuardCoreServiceClient::connect(endpoint.clone()).await {
            Ok(client) => return client,
            Err(error) => {
                last_error = Some(error);
                sleep(Duration::from_millis(20)).await;
            }
        }
    }

    panic!("server did not become ready: {last_error:?}");
}

async fn read_event_log_with_retry(path: &std::path::Path) -> String {
    let mut last_error = None;
    for _ in 0..50 {
        match tokio::fs::read_to_string(path).await {
            Ok(content) if !content.trim().is_empty() => return content,
            Ok(_) => sleep(Duration::from_millis(20)).await,
            Err(error) => {
                last_error = Some(error);
                sleep(Duration::from_millis(20)).await;
            }
        }
    }

    panic!("event log was not written: {last_error:?}");
}
