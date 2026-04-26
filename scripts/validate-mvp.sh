#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

printf '==> Kotlin Gateway tests\n'
(
  cd "$ROOT_DIR/gateway"
  ./gradlew test integrationTest
)

printf '\n==> Rust workspace tests\n'
(
  cd "$ROOT_DIR/rust"
  cargo test
)

printf '\nMVP validation completed successfully.\n'
