# Provider-Agnostic AI Guard Gateway Detailed Implementation Plan

## Purpose

이 문서는 상위 계획 `plans/2026-04-26-universal-ai-boundary-guard-implementation-v1.md`의 최신 구조에 맞춰, 실제 구현 순서와 브랜치/worktree 전략을 정의한다.

최신 제품 구조는 3계층이다.

1. **MVP 본체**: Gateway + Policy Decision + Audit
2. **확장 모듈**: Permission-aware Context Filter, High-Risk Action Review
3. **검증 도구**: Regression / Eval Pack

MVP 본체는 **Provider-agnostic AI Guard Gateway**이며, text payload 기반 `/guard/check` API를 통해 `ALLOW`, `REDACT`, `BLOCK` 결정을 반환하고 감사 로그에 원문 없이 기록한다.

## Current Baseline

- Current main baseline: `f3f62a0 merge: monorepo scaffold`
- Existing scaffold branch already merged: `chore/monorepo-scaffold`
- Existing Kotlin Gateway scaffold: `gateway/`
- Existing Rust workspace scaffold: `rust/`
- Existing protobuf placeholder: `proto/guard/v1/.gitkeep`

## Scope Alignment With Updated High-Level Plan

### Keep In MVP Body

- Single AI ingress shape
- Provider abstraction contract and metadata
- `/guard/check` text payload API
- Rust text payload normalize / detect / score / decide
- `ALLOW`, `REDACT`, `BLOCK`
- Policy decision evidence
- Audit logging without raw sensitive payload
- Read-only decision dashboard

### Move Out Of MVP Body

- Permission-aware context filtering: optional extension module
- High-risk action review: optional extension module
- Regression / Eval Pack: verification tooling, not runtime path
- File / archive / APT guard: long-term roadmap

## Non-Negotiable Implementation Rules

- 모든 구현 작업은 기능별 브랜치를 만든 뒤 진행한다.
- 각 기능 브랜치는 검증 후 `main`에 머지한다.
- 이전 단계 산출물이 필요한 작업은 반드시 선행 단계가 `main`에 머지된 뒤 시작한다.
- 병렬 가능한 작업은 독립 브랜치와 git worktree로 분리한다.
- Controller에는 오케스트레이션 로직을 넣지 않는다.
- Kotlin Application Service와 Rust Domain/Application Service 내부에 private helper 중심 구조를 만들지 않는다.
- Kotlin은 Gateway, API, provider abstraction, policy 저장/조회, audit, UI를 담당한다.
- Rust는 text payload normalize, detect, redact, score, decision 생성을 담당한다.
- 원문 민감정보는 audit log에 저장하지 않는다.
- Extension module은 MVP 본체 contract를 깨지 않는 방향으로 별도 단계에서 붙인다.

## Branch and Worktree Strategy

### Branch Naming

```text
plan/*        계획 문서 수정
chore/*       빌드, 설정, CI, 스캐폴딩
contract/*    protobuf/API contract
core/*        Rust Guard Core
service/*     Rust Core Service
gateway/*     Kotlin Gateway
policy/*      정책 관리
audit/*       감사 로그/모니터링
test/*        fixture/regression/e2e
extension/*   선택 확장 모듈
```

### Worktree Layout

```text
../uai-bg-contract
../uai-bg-rust-core
../uai-bg-rust-service
../uai-bg-kotlin-gateway
../uai-bg-audit
../uai-bg-regression
../uai-bg-extension-context
../uai-bg-extension-action
```

### Worktree Rules

- 동시에 수정하는 파일 경로가 겹치면 병렬 작업하지 않는다.
- `proto/` contract 변경은 항상 contract branch에서만 수행한다.
- Kotlin/Rust 구현 브랜치는 contract branch가 `main`에 merge된 뒤 시작한다.
- 병렬 브랜치는 항상 최신 `main`에서 생성한다.
- 병렬 작업 완료 후 merge 순서는 dependency order를 따른다.

## Dependency Graph

```text
0. Updated plan alignment
  ↓
1. Scaffold alignment check
  ↓
2. Contract first design
  ↓
3A. Rust core pure library
  ↓
3B. Rust gRPC core service
  ↓
5. Integration tests

2. Contract first design
  ↓
4A. Kotlin generated contract wiring
  ↓
4B. Kotlin RustCoreClient
  ↓
4C. /guard/check API
  ↓
7A. Audit persistence
  ↓
7B. Dashboard read-only

3A. Rust core pure library
  ↓
6. Regression / Eval Pack tooling

5 + 6 + 7B
  ↓
8. MVP hardening
  ↓
9A. Optional context filter extension
  ↓
9B. Optional action review extension
```

## Stage 0. Updated Plan Alignment

### Branch

`plan/align-detailed-plan-to-modular-scope`

### Status

In progress in this document.

### Goal

외부 수정된 상위 계획의 3계층 구조를 세부 구현 계획에 반영한다.

### Tasks

- [x] 외부 수정된 상위 계획을 재확인한다.
- [x] 기존 세부 계획과 최신 상위 계획의 차이를 정리한다.
- [x] MVP 본체 / 확장 모듈 / 검증 도구 구조로 세부 계획을 재작성한다.
- [ ] 기존 Stage 1 스캐폴드가 새 계획에 어긋나는지 검증한다.
- [ ] 필요한 스캐폴드 수정 후 검증한다.

### Merge Gate

- 이 계획 파일이 `main`에 merge되어야 후속 구현을 진행한다.

---

## Stage 1. Scaffold Alignment Check

### Branch

`chore/align-scaffold-to-modular-scope`

### Must Start After

- Stage 0 is merged into `main`.

### Goal

이미 구현된 monorepo scaffold가 최신 상위 계획의 MVP 본체 중심 구조에 맞는지 확인하고, 과도한 확장 모듈 흔적을 제거하거나 후순위로 분리한다.

### Current Existing Files To Check

- `gateway/build.gradle.kts`
- `gateway/settings.gradle.kts`
- `gateway/src/main/kotlin/com/boundaryguard/gateway/BoundaryGatewayApplication.kt`
- `gateway/src/test/kotlin/com/boundaryguard/gateway/BoundaryGatewayApplicationTests.kt`
- `rust/Cargo.toml`
- `rust/boundary-core/Cargo.toml`
- `rust/boundary-core/src/lib.rs`
- `rust/boundary-core-service/Cargo.toml`
- `rust/boundary-core-service/src/main.rs`
- `proto/guard/v1/.gitkeep`

### Tasks

- [ ] Kotlin build target이 실행 환경과 계획에 맞는지 재확인한다.
- [ ] 필요하면 Java 21 목표와 로컬 Java 17 검증 사이의 차이를 명시한다.
- [ ] Gateway 패키지 구조가 MVP 본체 중심인지 확인한다.
- [ ] 확장 모듈 패키지는 아직 만들지 않는다.
- [ ] Rust workspace가 `boundary-core`와 `boundary-core-service`로 충분히 분리되어 있는지 확인한다.
- [ ] proto placeholder가 Stage 2 contract 작업을 방해하지 않는지 확인한다.
- [ ] `.gitignore`가 Gradle/Rust 산출물을 올바르게 제외하는지 확인한다.

### Verification

- [ ] `gateway/gradlew test` 통과
- [ ] `cargo test` 통과
- [ ] `git status --ignored --short`에서 build 산출물이 ignored 처리된다.
- [ ] 스캐폴드에 context/action/file/APT 장기 로드맵 구현물이 섞여 있지 않다.

### Merge Gate

- Stage 1 alignment가 `main`에 merge되어야 Stage 2 contract를 시작한다.

---

## Stage 2. Contract First Design

### Branch

`contract/guard-check-v1`

### Must Start After

- Stage 1 is merged into `main`.

### Worktree

`../uai-bg-contract`

### Goal

MVP 본체용 `GuardCheckRequest` / `GuardCheckResult` protobuf contract를 먼저 고정한다.

### Contract Boundary

MVP contract는 본체에 필요한 필드만 활성화한다.

포함:

- text payload 검사
- provider metadata
- principal metadata
- policy revision
- decision evidence
- redaction result
- health check

제외 또는 reserved:

- context candidate filtering result
- action context
- file attachment
- review/quarantine 실행 흐름

### Tasks

- [ ] `proto/guard/v1/guard.proto`를 작성한다.
- [ ] `DecisionType`을 `ALLOW`, `REDACT`, `BLOCK` 중심으로 정의한다.
- [ ] `REVIEW`, `QUARANTINE`은 reserved comment 또는 extension enum 후보로만 둔다.
- [ ] `PayloadType` MVP 값을 `TEXT`, `PROMPT`, `RESPONSE`, `DATA_EGRESS`로 정의한다.
- [ ] `ProviderType` MVP 값을 `ANTHROPIC`, `OPENAI_COMPATIBLE`, `GOOGLE`, `INTERNAL`로 정의한다.
- [ ] `PrincipalContext`를 정의한다.
- [ ] `ProviderMetadata`를 정의한다.
- [ ] `ViolationEvidence`를 정의한다.
- [ ] `RedactionResult`를 정의한다.
- [ ] `GuardCheckRequest`를 정의한다.
- [ ] `GuardCheckResult`를 정의한다.
- [ ] `CoreHealthCheckRequest`와 `CoreHealthCheckResult`를 정의한다.
- [ ] `GuardCoreService.Check` RPC를 정의한다.
- [ ] `GuardCoreService.Health` RPC를 정의한다.
- [ ] policy sync RPC는 MVP에서 제외하거나 별도 proto placeholder로만 둔다.

### Contract Draft Shape

```text
GuardCheckRequest
  request_id
  payload_type
  content
  provider_metadata
  principal_context
  policy_revision

GuardCheckResult
  request_id
  decision
  risk_score
  violations
  redaction_result
  policy_revision
  core_latency_ms
```

### Verification

- [ ] protobuf compile 또는 Buf lint 중 하나가 가능하다.
- [ ] Kotlin/Rust codegen 위치가 결정되어 있다.
- [ ] contract에 원문 audit 저장 필드가 없다.
- [ ] context/action/file 관련 필드는 MVP contract에서 제외되어 있다.

### Merge Gate

- Stage 2가 `main`에 merge되어야 Rust/Kotlin 구현 브랜치를 시작한다.

---

## Stage 3A. Rust Guard Core Pure Library

### Branch

`core/rust-guard-pipeline`

### Must Start After

- Stage 2 is merged into `main`.

### Worktree

`../uai-bg-rust-core`

### Can Run In Parallel With

- Stage 4A Kotlin generated contract wiring

### Goal

gRPC와 분리된 순수 Rust Guard Core library에서 text payload 정책 결정을 구현한다.

### Tasks

- [ ] 기존 placeholder `GuardCore`를 실제 pipeline 진입점으로 교체한다.
- [ ] `GuardInput` 내부 모델을 만든다.
- [ ] `GuardOutput` 내부 모델을 만든다.
- [ ] `Decision` 내부 enum을 만든다.
- [ ] `Normalizer` module을 만든다.
- [ ] Unicode normalization을 구현한다.
- [ ] URL encoding decode 후보 생성을 구현한다.
- [ ] HTML entity decode 후보 생성을 구현한다.
- [ ] Base64 후보 탐지를 구현한다.
- [ ] `Detector` trait 또는 enum dispatcher를 만든다.
- [ ] Prompt Injection Detector를 구현한다.
- [ ] PII Detector를 구현한다.
- [ ] Secret Detector를 구현한다.
- [ ] Redactor를 구현한다.
- [ ] Risk Scorer를 구현한다.
- [ ] Decision Aggregator를 구현한다.
- [ ] 모든 detector output에 evidence를 포함한다.

### Verification

- [ ] `cargo test` 통과
- [ ] detector별 단위 테스트 통과
- [ ] redaction 테스트 통과
- [ ] 같은 입력에 같은 decision이 나온다.
- [ ] evidence에는 원문 전체가 아니라 match 위치/유형/요약만 포함한다.

### Merge Gate

- Stage 3A가 `main`에 merge되어야 Stage 3B service 구현과 Stage 6 검증 도구를 본격 진행한다.

---

## Stage 3B. Rust Core gRPC Service

### Branch

`service/rust-core-grpc`

### Must Start After

- Stage 2 is merged into `main`.
- Stage 3A is merged into `main`.

### Worktree

`../uai-bg-rust-service`

### Goal

Rust Core library를 tonic 기반 gRPC service로 노출한다.

### Tasks

- [ ] `boundary-core-service`에 tonic/prost build 설정을 추가한다.
- [ ] protobuf Rust codegen을 구성한다.
- [ ] proto request를 Rust internal model로 변환한다.
- [ ] Rust internal output을 proto result로 변환한다.
- [ ] `Check` RPC를 구현한다.
- [ ] `Health` RPC를 구현한다.
- [ ] request timeout 처리 기준을 정한다.
- [ ] structured log에 request id를 포함한다.

### Verification

- [ ] `cargo test` 통과
- [ ] gRPC service boot smoke test 통과
- [ ] `Check` RPC가 `ALLOW`, `REDACT`, `BLOCK` fixture를 반환한다.
- [ ] `Health` RPC가 core version과 readiness를 반환한다.

### Merge Gate

- Kotlin RustCoreClient 구현 전 `main`에 merge되어야 한다.

---

## Stage 4A. Kotlin Generated Contract Wiring

### Branch

`gateway/contract-wiring`

### Must Start After

- Stage 2 is merged into `main`.

### Worktree

`../uai-bg-kotlin-gateway`

### Can Run In Parallel With

- Stage 3A Rust Guard Core Pure Library

### Goal

기존 Kotlin Gateway scaffold에 protobuf/gRPC generated code wiring을 추가한다.

### Tasks

- [ ] `gateway/build.gradle.kts`에 protobuf/gRPC Kotlin 또는 Java codegen 설정을 추가한다.
- [ ] generated source directory를 Gradle source set에 연결한다.
- [ ] contract compile task를 Gradle build에 포함한다.
- [ ] Kotlin compile이 generated contract에 접근 가능한지 확인한다.
- [ ] 기존 Spring context load test를 유지한다.

### Verification

- [ ] `gateway/gradlew test` 통과
- [ ] generated code가 직접 커밋되지 않는다.
- [ ] Controller 또는 Application Service 구현은 아직 추가하지 않는다.

### Merge Gate

- Stage 4B, 4C 시작 전 `main`에 merge되어야 한다.

---

## Stage 4B. Kotlin RustCoreClient

### Branch

`gateway/rust-core-client`

### Must Start After

- Stage 3B is merged into `main`.
- Stage 4A is merged into `main`.

### Worktree

`../uai-bg-kotlin-gateway`

### Goal

Kotlin Gateway에서 Rust Core gRPC service를 안정적으로 호출한다.

### Tasks

- [ ] `RustCoreClient` interface를 정의한다.
- [ ] gRPC client implementation을 만든다.
- [ ] timeout 설정을 구현한다.
- [ ] error mapping을 구현한다.
- [ ] fail-closed / fail-open 설정 model을 만든다.
- [ ] request id propagation을 구현한다.
- [ ] client integration test를 구성한다.

### Verification

- [ ] Rust Core test server 또는 fake server 기반 integration test 통과
- [ ] timeout 시 설정에 따라 `BLOCK` 또는 configured fallback이 적용된다.
- [ ] gRPC error가 API error로 직접 누출되지 않는다.

### Merge Gate

- Stage 4C 시작 전 `main`에 merge되어야 한다.

---

## Stage 4C. `/guard/check` API

### Branch

`gateway/guard-check-api`

### Must Start After

- Stage 4B is merged into `main`.

### Worktree

`../uai-bg-kotlin-gateway`

### Goal

외부 호출자가 text payload를 검사할 수 있는 MVP API를 제공한다.

### Tasks

- [ ] `/guard/check` request DTO를 정의한다.
- [ ] `/guard/check` response DTO를 정의한다.
- [ ] Controller를 구현한다.
- [ ] Controller는 DTO 변환만 담당한다.
- [ ] `GuardCheckApplicationService`를 구현한다.
- [ ] DTO를 protobuf request로 변환한다.
- [ ] RustCoreClient 호출을 연결한다.
- [ ] GuardCheckResult를 API response로 변환한다.
- [ ] provider metadata 검증을 추가한다.
- [ ] principal metadata 검증을 추가한다.

### Verification

- [ ] `/guard/check` API happy path 테스트 통과
- [ ] `REDACT` response에 redacted payload가 포함된다.
- [ ] `BLOCK` response에 evidence summary가 포함된다.
- [ ] 민감 원문이 log에 출력되지 않는다.

### Merge Gate

- Stage 5 integration과 Stage 7 audit/dashboard 시작 전 `main`에 merge되어야 한다.

---

## Stage 5. End-to-End Integration

### Branch

`test/guard-check-e2e`

### Must Start After

- Stage 3B is merged into `main`.
- Stage 4C is merged into `main`.

### Worktree

`../uai-bg-regression`

### Goal

Kotlin Gateway → Rust Core Service → Rust Core Library 전체 MVP 본체 흐름을 검증한다.

### Tasks

- [ ] local integration 실행 방식을 정한다.
- [ ] Rust Core Service를 테스트 중 기동하는 방식을 정한다.
- [ ] `ALLOW` e2e fixture를 만든다.
- [ ] `REDACT` e2e fixture를 만든다.
- [ ] `BLOCK` e2e fixture를 만든다.
- [ ] core unavailable case를 테스트한다.
- [ ] request id가 전체 흐름에서 유지되는지 테스트한다.

### Verification

- [ ] Kotlin test에서 실제 또는 test Rust Core Service 호출 성공
- [ ] 세 decision 경로가 모두 검증됨
- [ ] core latency 또는 최소 timing metadata가 response/audit 후보에 포함됨

### Merge Gate

- MVP hardening 전 반드시 `main`에 merge한다.

---

## Stage 6. Regression / Eval Pack Tooling

### Branch

`test/regression-eval-pack`

### Must Start After

- Stage 3A is merged into `main`.

### Worktree

`../uai-bg-regression`

### Can Run In Parallel With

- Stage 3B
- Stage 4B
- Stage 4C

### Goal

runtime 본체와 분리된 검증 도구로 detector, normalization, provider request matrix 회귀를 고정한다.

### Tasks

- [ ] fixture 디렉터리 구조를 만든다.
- [ ] prompt injection fixture를 만든다.
- [ ] base64 prompt injection fixture를 만든다.
- [ ] URL encoded prompt injection fixture를 만든다.
- [ ] HTML entity fixture를 만든다.
- [ ] Unicode zero-width / spacing 우회 fixture를 만든다.
- [ ] PII fixture를 만든다.
- [ ] Secret fixture를 만든다.
- [ ] provider request matrix fixture를 만든다.
- [ ] expected decision format을 정의한다.
- [ ] fixture runner를 Rust test와 연결한다.

### Verification

- [ ] `cargo test`에서 fixture regression 실행
- [ ] false positive/false negative 후보를 별도 목록으로 관리
- [ ] fixture에는 실제 secret이나 실제 개인정보를 넣지 않는다.
- [ ] runtime API path에 검증 도구 의존성이 섞이지 않는다.

### Merge Gate

- Stage 8 MVP hardening 전 `main`에 merge한다.

---

## Stage 7A. Audit Persistence

### Branch

`audit/persistence-mvp`

### Must Start After

- Stage 4C is merged into `main`.

### Worktree

`../uai-bg-audit`

### Can Run In Parallel With

- Stage 6 Regression / Eval Pack

### Goal

Guard decision 결과를 원문 없이 저장한다.

### Tasks

- [ ] AuditEvent model을 정의한다.
- [ ] DB migration 도구를 선택한다.
- [ ] PostgreSQL schema를 정의한다.
- [ ] content hash 저장 방식을 구현한다.
- [ ] redacted summary 저장 방식을 구현한다.
- [ ] violation detail 저장 구조를 구현한다.
- [ ] provider/model/principal/payload type/decision 필드를 저장한다.
- [ ] core latency를 저장한다.
- [ ] retention 정책 placeholder를 둔다.

### Audit Must Not Store

- [ ] full prompt 원문
- [ ] full response 원문
- [ ] full DB/RAG/tool result 원문
- [ ] raw secret/token/private key

### Verification

- [ ] Testcontainers PostgreSQL 테스트 통과
- [ ] `BLOCK` / `REDACT` event 저장 테스트 통과
- [ ] 원문 민감정보 저장 방지 테스트 통과

### Merge Gate

- Stage 7B dashboard와 Stage 8 hardening 전 `main`에 merge한다.

---

## Stage 7B. Read-Only Dashboard

### Branch

`audit/dashboard-readonly`

### Must Start After

- Stage 7A is merged into `main`.

### Worktree

`../uai-bg-audit`

### Goal

Thymeleaf 기반으로 Guard decision 결과를 조회한다.

### Tasks

- [ ] dashboard route를 정의한다.
- [ ] decision summary 화면을 만든다.
- [ ] recent events 화면을 만든다.
- [ ] event detail 화면을 만든다.
- [ ] core health 표시 영역을 만든다.
- [ ] policy hit 표시 placeholder를 만든다.
- [ ] 관리자 인증 placeholder를 둔다.

### Verification

- [ ] Thymeleaf rendering test 통과
- [ ] event detail에 원문 payload가 표시되지 않는다.
- [ ] block/redact count가 표시된다.

### Merge Gate

- MVP hardening 전 `main`에 merge한다.

---

## Stage 8. MVP Hardening

### Branch

`chore/mvp-hardening`

### Must Start After

- Stage 5 is merged into `main`.
- Stage 6 is merged into `main`.
- Stage 7A is merged into `main`.
- Stage 7B is merged into `main`.

### Goal

MVP 본체를 실행 가능한 상태로 고정한다.

### Tasks

- [ ] root-level build/test command를 정리한다.
- [ ] Kotlin build/test를 실행한다.
- [ ] Rust build/test를 실행한다.
- [ ] integration test를 실행한다.
- [ ] regression fixture test를 실행한다.
- [ ] known limitations를 계획 문서에 반영한다.
- [ ] 다음 확장 module 시작 조건을 정리한다.

### Verification

- [ ] 전체 Kotlin test 통과
- [ ] 전체 Rust test 통과
- [ ] `/guard/check` e2e 통과
- [ ] audit 원문 저장 금지 테스트 통과
- [ ] dashboard read-only 조회 통과

### Merge Gate

- 모든 검증 통과 후 `main`에 merge한다.

---

## Extension Stage 9A. Permission-Aware Context Filter Module

### Branch

`extension/context-filter`

### Must Start After

- Stage 8 is merged into `main`.

### Goal

상위 retrieval 시스템이 넘긴 context candidate를 prompt 구성 직전에 필터링하는 선택 모듈을 추가한다.

### Tasks

- [ ] `ContextCandidate` contract를 추가한다.
- [ ] `ContextFilterResult` contract를 추가한다.
- [ ] principal, tenant, sensitivity, source metadata 기반 필터 규칙을 정의한다.
- [ ] Gateway에 context filtering hook을 추가한다.
- [ ] context candidate count와 filtered count를 audit에 저장한다.
- [ ] permission-aware context filter 테스트를 구성한다.

### Merge Gate

- MVP 본체 contract와 runtime path를 깨지 않아야 한다.

---

## Extension Stage 9B. High-Risk Action Review Module

### Branch

`extension/action-review`

### Must Start After

- Stage 8 is merged into `main`.

### Goal

tool call, service call, command execution 같은 고위험 action을 실행 전 review 대상으로 승격한다.

### Tasks

- [ ] `ActionContext` contract를 추가한다.
- [ ] action type taxonomy를 정의한다.
- [ ] action risk model을 정의한다.
- [ ] DB query intent를 action context로 검사한다.
- [ ] service call intent를 action context로 검사한다.
- [ ] tool call intent를 action context로 검사한다.
- [ ] code change diff를 action context로 검사한다.
- [ ] command execution request를 action context로 검사한다.
- [ ] tenant/user scope 조건 검사 정책을 설계한다.
- [ ] destructive SQL/command 탐지 규칙을 정의한다.
- [ ] high-risk action에 대해 `REVIEW` decision을 도입한다.

### Merge Gate

- 실제 tool/service/command 실행기는 구현하지 않는다.
- Guard는 decision과 evidence만 반환한다.

---

## Future Roadmap After Extension Modules

아래 항목은 MVP와 2개 확장 모듈 안정화 후 별도 계획으로 진행한다.

- [ ] File / archive guard
- [ ] document extraction
- [ ] advanced threat detection
- [ ] sandbox / AV / SIEM integration
- [ ] MCP schema integrity / rug-pull detection
- [ ] `QUARANTINE` decision 활성화
- [ ] APT-style dashboard

## Parallel Execution Plan

### Parallel Window A: After Stage 2 Merge

```text
main
 ├─ core/rust-guard-pipeline       worktree: ../uai-bg-rust-core
 └─ gateway/contract-wiring        worktree: ../uai-bg-kotlin-gateway
```

Constraints:

- contract 파일은 이 window에서 변경하지 않는다.
- regression tooling은 Stage 3A merge 후 시작한다.

### Parallel Window B: After Stage 3A Merge

```text
main
 ├─ service/rust-core-grpc         worktree: ../uai-bg-rust-service
 └─ test/regression-eval-pack      worktree: ../uai-bg-regression
```

Constraints:

- service branch는 Stage 3A와 Stage 2 contract에 의존한다.
- regression branch는 Rust core API 변경을 따라간다.

### Parallel Window C: After Stage 4C Merge

```text
main
 ├─ test/guard-check-e2e           worktree: ../uai-bg-regression
 └─ audit/persistence-mvp          worktree: ../uai-bg-audit
```

Constraints:

- Dashboard는 AuditEvent model이 merge된 뒤 시작한다.

### Parallel Window D: After Stage 8 Merge

```text
main
 ├─ extension/context-filter       worktree: ../uai-bg-extension-context
 └─ extension/action-review        worktree: ../uai-bg-extension-action
```

Constraints:

- 두 확장 모듈이 contract를 동시에 변경하려면 먼저 contract extension branch를 별도로 둔다.

## Merge Order

```text
0. plan/align-detailed-plan-to-modular-scope
1. chore/align-scaffold-to-modular-scope
2. contract/guard-check-v1
3. core/rust-guard-pipeline
4. gateway/contract-wiring
5. service/rust-core-grpc
6. gateway/rust-core-client
7. gateway/guard-check-api
8. test/regression-eval-pack
9. test/guard-check-e2e
10. audit/persistence-mvp
11. audit/dashboard-readonly
12. chore/mvp-hardening
13. extension/context-filter
14. extension/action-review
```

## First Implementation Slice After Plan Alignment

다음 구현 브랜치는 이미 구현된 scaffold를 최신 계획 기준으로 재검증/수정하는 단계다.

```text
chore/align-scaffold-to-modular-scope
```

이후 `contract/guard-check-v1`로 넘어간다.

## Definition of Done for MVP Body

- [ ] `/guard/check` API가 text payload를 받는다.
- [ ] Kotlin Gateway가 Rust Core Service를 호출한다.
- [ ] Rust Core가 text payload를 normalize한다.
- [ ] Rust Core가 prompt injection, PII, secret을 탐지한다.
- [ ] Rust Core가 `ALLOW`, `REDACT`, `BLOCK` 중 하나를 반환한다.
- [ ] `REDACT` 결과에는 redacted content가 포함된다.
- [ ] `BLOCK` 결과에는 evidence summary가 포함된다.
- [ ] Audit log에는 원문 민감정보가 저장되지 않는다.
- [ ] Dashboard에서 decision event를 read-only로 볼 수 있다.
- [ ] Regression / Eval Pack이 runtime과 분리되어 실행된다.
- [ ] Rust/Kotlin/integration/regression test가 통과한다.

## Explicitly Out of MVP Body Scope

- [ ] Permission-aware context filtering runtime 적용
- [ ] High-risk action review runtime 적용
- [ ] 실제 LLM provider proxying
- [ ] 실제 AI provider 호출
- [ ] DB 직접 조회
- [ ] Tool execution
- [ ] Service call execution
- [ ] Code modification execution
- [ ] File attachment parsing
- [ ] Archive extraction
- [ ] OCR
- [ ] Sandbox detonation
- [ ] SIEM/SOAR 연동
- [ ] MCP tool rug-pull detection
- [ ] APT dashboard
