use std::path::PathBuf;
use std::sync::Arc;

use boundary_core::GuardCore;
use boundary_core_service::event::{noop_publisher, JsonlGuardEventPublisher};
use boundary_core_service::{run_server_with_event_publisher, DEFAULT_BIND_ADDR};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let bind_addr = std::env::var("BOUNDARY_CORE_BIND_ADDR").unwrap_or_else(|_| DEFAULT_BIND_ADDR.to_string());
    let event_publisher = match std::env::var("BOUNDARY_GUARD_EVENT_LOG") {
        Ok(path) if !path.trim().is_empty() => Arc::new(JsonlGuardEventPublisher::new(PathBuf::from(path))),
        _ => noop_publisher(),
    };

    run_server_with_event_publisher(bind_addr.parse()?, GuardCore::new(), event_publisher).await
}
