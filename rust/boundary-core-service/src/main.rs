use boundary_core::GuardCore;
use boundary_core_service::{run_server, DEFAULT_BIND_ADDR};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let bind_addr = std::env::var("BOUNDARY_CORE_BIND_ADDR").unwrap_or_else(|_| DEFAULT_BIND_ADDR.to_string());
    run_server(bind_addr.parse()?, GuardCore::new()).await
}
