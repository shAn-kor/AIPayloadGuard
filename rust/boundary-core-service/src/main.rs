pub mod guard {
    pub mod v1 {
        tonic::include_proto!("guard.v1");
    }
}

use boundary_core::GuardCore;

fn main() {
    let _core = GuardCore::new();
}
