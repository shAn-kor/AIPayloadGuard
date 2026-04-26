pub struct GuardCore;

impl GuardCore {
    pub fn new() -> Self {
        Self
    }
}

impl Default for GuardCore {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::GuardCore;

    #[test]
    fn creates_guard_core() {
        let _core = GuardCore::new();
    }
}
