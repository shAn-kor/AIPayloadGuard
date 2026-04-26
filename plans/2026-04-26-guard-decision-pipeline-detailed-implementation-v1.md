# Guard Decision Pipeline Detailed Implementation Plan

## Purpose

이 문서는 `Universal AI Boundary Guard`의 1차 핵심 기능인 **Guard Decision Pipeline**을 실제 구현 가능한 단위로 쪼갠 세부 계획이다.

현재 상위 계획은 다음 파일을 기준으로 한다.

- `plans/2026-04-26-universal-ai-boundary-guard-implementation-v1.md`

## Baseline

- Baseline commit: `72bda5b docs: add initial guard implementation plan`
- Main branch: `main`
- Core MVP target: text payload 기반 `/guard/check`
- MVP decisions: `ALLOW`, `REDACT`, `BLOCK`
- Extension decisions: `REVIEW`, `QUARANTINE`

## Non-Negotiable Implementation Rules

- 모든 구현 작업은 기능별 브랜치를 만든 뒤 진행한다.
- 각 기능 브랜치는 검증 후 `main`에 머지한다.
- 이전 단계 산출물이 필요한 작업은 반드시 선행 단계가 `main`에 머지된 뒤 시작한다.
- 병렬 가능한 작업은 독립 브랜치와 git worktree로 분리한다.
- Controller에는 오케스트레이션 로직을 넣지 않는다.
- Kotlin Application Service와 Rust Domain/Application Service 내부에 private helper 중심 구조를 만들지 않는다.
- Kotlin은 Gateway, API, audit, policy 저장/조회, UI를 담당한다.
- Rust는 normalize, detect, redact, score, decision 생성을 담당한다.
- 원문 민감정보는 audit log에 저장하지 않는다.

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
```

### Worktree Layout

작업 실행 단계에서는 repository 바깥에 worktree를 둔다.

```text
../uai-bg-contract
../uai-bg-rust-core
../uai-bg-rust-service
../uai-bg-kotlin-gateway
../uai-bg-audit
../uai-bg-regression
```

### Worktree Usage Rule

- 동시에 수정하는 파일 경로가 겹치면 병렬 작업하지 않는다.
- `proto/` contract가 바뀌는 동안 Kotlin/Rust 구현 브랜치는 contract branch merge 전까지 시작하지 않는다.
- 병렬 브랜치는 항상 최신 `main`에서 생성한다.
- 병렬 작업 완료 후 merge 순서는 dependency order를 따른다.

## Dependency Graph

```text
0. Repository baseline
  ↓
1. Monorepo scaffold
  ↓
2. Contract first design
  ↓
3A. Rust core pure library
  ↓
3B. Rust gRPC service
  ↓
5. Integration tests

2. Contract first design
  ↓
4A. Kotlin gateway skeleton
  ↓
4B. Kotlin RustCoreClient
  ↓
4C. /guard/check API
  ↓
5. Integration tests

3A. Rust core pure library
  ├─ 6A. detector fixtures
  ├─ 6B. normalization fixtures
  └─ 6C. redaction fixtures

4C. /guard/check API
  ├─ 7A. audit persistence
  └─ 7B. dashboard read-only

5 + 6 + 7
  ↓
8. MVP hardening and release candidate
```

## Stage 0. Baseline Commit

### Status

Completed.

### Evidence

- Initial plan committed at baseline commit `72bda5b`.

### Gate to Next Stage

- `main` contains `.gitignore` and high-level implementation plan.
- Working tree is clean before starting scaffold work.

---

## Stage 1. Monorepo Scaffold

### Branch

`chore/monorepo-scaffold`

### Must Start After

- Stage 0 is complete.

### Worktree

Not required unless another independent documentation task is running.

### Goal

Kotlin Spring, Rust, protobuf contract, plans, and future local tooling이 공존할 수 있는 monorepo 기본 구조를 만든다.

### Tasks

- [ ] 루트 디렉터리 구조를 확정한다.
- [ ] `proto/` 디렉터리를 생성한다.
- [ ] `rust/` 또는 `core/` 계열 Rust workspace 위치를 정한다.
- [ ] `gateway/` Kotlin Spring 위치를 정한다.
- [ ] Gradle Kotlin DSL 사용 여부를 확정한다.
- [ ] Rust workspace 사용 여부를 확정한다.
- [ ] local dev용 환경 변수 이름만 정의하고 secret 값은 넣지 않는다.
- [ ] `.gitignore`에 필요한 scaffold 산출물 제외 규칙을 보강한다.

### Expected Structure

```text
.
├── proto/
│   └── guard/v1/
├── rust/
│   ├── Cargo.toml
│   ├── boundary-core/
│   └── boundary-core-service/
├── gateway/
│   ├── build.gradle.kts
│   └── src/
├── plans/
└── .gitignore
```

### Verification

- [ ] `git status`에 의도한 scaffold 파일만 표시된다.
- [ ] 빈 secret/config 값이 커밋되지 않는다.
- [ ] Kotlin/Rust 빌드 파일이 아직 생성되지 않았다면 이유가 계획에 남아 있다.

### Merge Gate

- scaffold 구조가 `main`에 merge되어야 Stage 2를 시작한다.

---

## Stage 2. Contract First Design

### Branch

`contract/guard-check-v1`

### Must Start After

- Stage 1 is merged into `main`.

### Worktree

`../uai-bg-contract`

### Goal

Kotlin Gateway와 Rust Core가 공유할 `GuardCheckRequest` / `GuardCheckResult` contract를 먼저 고정한다.

### Tasks

- [ ] `guard/v1/guard.proto`를 작성한다.
- [ ] `DecisionType`을 `ALLOW`, `REDACT`, `BLOCK`으로 제한한다.
- [ ] 확장용 enum 값은 예약하거나 주석으로만 남긴다.
- [ ] `PayloadType` MVP 값을 정의한다.
- [ ] `ProviderType` MVP 값을 정의한다.
- [ ] `PrincipalContext`를 정의한다.
- [ ] `ProviderMetadata`를 정의한다.
- [ ] `ContextCandidate`를 정의한다.
- [ ] `ContextFilterResult`를 정의한다.
- [ ] `ViolationEvidence`를 정의한다.
- [ ] `RedactionResult`를 정의한다.
- [ ] `GuardCoreService.Check` RPC를 정의한다.
- [ ] `GuardCoreService.Health` RPC를 정의한다.
- [ ] `PolicyService.ValidatePolicyBundle` / `SyncPolicyBundle`는 MVP placeholder로 둘지 결정한다.

### Contract Draft Shape

```text
GuardCheckRequest
  request_id
  payload_type
  content
  provider_metadata
  principal_context
  context_candidates
  policy_revision

GuardCheckResult
  request_id
  decision
  risk_score
  violations
  redaction_result
  context_filter_result
  policy_revision
  core_latency_ms
```

### Verification

- [ ] protobuf lint 또는 최소 compile 검증이 가능하다.
- [ ] Kotlin/Rust codegen 경로가 정해져 있다.
- [ ] contract에 원문 audit 저장 필드가 없다.
- [ ] `REVIEW`, `QUARANTINE`은 MVP에서 활성 decision이 아니다.

### Merge Gate

- Contract branch가 `main`에 merge되어야 Rust/Kotlin 구현이 시작된다.

---

## Stage 3A. Rust Core Pure Library

### Branch

`core/rust-guard-pipeline`

### Must Start After

- Stage 2 is merged into `main`.

### Worktree

`../uai-bg-rust-core`

### Can Run In Parallel With

- Stage 4A Kotlin Gateway Skeleton
- Stage 6 fixture 설계 일부

단, protobuf contract는 변경하지 않는다.

### Goal

gRPC와 분리된 순수 Rust Guard Core library를 구현한다.

### Tasks

- [ ] Rust workspace에 `boundary-core` crate를 만든다.
- [ ] `GuardInput` 내부 모델을 만든다.
- [ ] `GuardOutput` 내부 모델을 만든다.
- [ ] `Normalizer` trait 또는 module을 만든다.
- [ ] Unicode normalization을 구현한다.
- [ ] URL encoding decode 후보 생성을 구현한다.
- [ ] HTML entity decode 후보 생성을 구현한다.
- [ ] Base64 후보 탐지를 구현한다.
- [ ] Prompt Injection Detector를 구현한다.
- [ ] PII Detector를 구현한다.
- [ ] Secret Detector를 구현한다.
- [ ] Redactor를 구현한다.
- [ ] Risk Scorer를 구현한다.
- [ ] Decision Aggregator를 구현한다.
- [ ] 모든 detector output에 evidence를 포함한다.

### Internal Pipeline

```text
GuardInput
  ↓
NormalizeCandidates
  ↓
DetectorFindings
  ↓
RiskScore
  ↓
Decision
  ↓
RedactionResult
  ↓
GuardOutput
```

### Initial Detector Rules

- [ ] `ignore previous instructions` 류 role override 탐지
- [ ] `reveal system prompt` 류 system prompt extraction 탐지
- [ ] `bypass policy`, `disable safety` 류 정책 우회 탐지
- [ ] email 탐지
- [ ] phone-like pattern 탐지
- [ ] token/API key-like pattern 탐지
- [ ] private key block 탐지
- [ ] high entropy secret 후보 탐지

### Verification

- [ ] `cargo test` 통과
- [ ] detector별 단위 테스트 통과
- [ ] redaction 테스트 통과
- [ ] 같은 입력에 같은 decision이 나온다.
- [ ] evidence에는 원문 전체가 아니라 match 위치/유형/요약만 포함한다.

### Merge Gate

- `boundary-core`가 gRPC 없이 독립 테스트 가능해야 Stage 3B가 시작된다.

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

Rust Core library를 gRPC service로 감싼다.

### Tasks

- [ ] `boundary-core-service` crate를 만든다.
- [ ] tonic 기반 gRPC server를 구성한다.
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

- Kotlin Gateway의 RustCoreClient integration 전 `main`에 merge되어야 한다.

---

## Stage 4A. Kotlin Gateway Skeleton

### Branch

`gateway/spring-skeleton`

### Must Start After

- Stage 2 is merged into `main`.

### Worktree

`../uai-bg-kotlin-gateway`

### Can Run In Parallel With

- Stage 3A Rust Core Pure Library
- Stage 6 fixture 설계 일부

### Goal

Kotlin Spring Boot 기반 Gateway skeleton과 패키지 경계를 만든다.

### Tasks

- [ ] Kotlin Spring Boot 프로젝트를 생성한다.
- [ ] Gradle Kotlin DSL을 구성한다.
- [ ] protobuf Kotlin/Java codegen을 구성한다.
- [ ] 패키지 구조를 만든다.
- [ ] Controller는 request/response DTO 변환만 담당하도록 위치를 제한한다.
- [ ] Application Service 패키지를 만든다.
- [ ] Core Client 패키지를 만든다.
- [ ] Audit 패키지를 만든다.
- [ ] Policy 패키지를 만든다.
- [ ] Admin 패키지를 만든다.
- [ ] Provider adapter abstraction 패키지를 만든다.

### Suggested Package Layout

```text
com.boundaryguard.gateway
  api
  application
  coreclient
  provider
  policy
  audit
  admin
  security
  monitoring
```

### Verification

- [ ] Gradle build 통과
- [ ] Spring context load test 통과
- [ ] Controller에 orchestration 코드가 없다.

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

- [ ] RustCoreClient interface를 정의한다.
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
- [ ] GuardCheckApplicationService를 구현한다.
- [ ] DTO를 protobuf request로 변환한다.
- [ ] RustCoreClient 호출을 연결한다.
- [ ] GuardCheckResult를 API response로 변환한다.
- [ ] provider metadata 검증을 추가한다.
- [ ] principal metadata 검증을 추가한다.
- [ ] permission-aware context filter hook을 최소 구현한다.

### Verification

- [ ] `/guard/check` API happy path 테스트 통과
- [ ] `REDACT` response에 redacted payload가 포함된다.
- [ ] `BLOCK` response에 evidence summary가 포함된다.
- [ ] 민감 원문이 log에 출력되지 않는다.

### Merge Gate

- Stage 5 integration, Stage 7 audit/dashboard 시작 전 `main`에 merge되어야 한다.

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

Kotlin Gateway → Rust Core Service → Rust Core Library 전체 흐름을 검증한다.

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

## Stage 6. Regression Fixture Pack

### Branch

`test/regression-fixtures`

### Must Start After

- Stage 3A is merged into `main`.

### Worktree

`../uai-bg-regression`

### Can Run In Parallel With

- Stage 3B
- Stage 4B
- Stage 4C

### Goal

초기 detector와 normalization 우회 케이스를 fixture로 고정한다.

### Tasks

- [ ] fixture 디렉터리 구조를 만든다.
- [ ] prompt injection fixture를 만든다.
- [ ] base64 prompt injection fixture를 만든다.
- [ ] URL encoded prompt injection fixture를 만든다.
- [ ] HTML entity fixture를 만든다.
- [ ] Unicode zero-width / spacing 우회 fixture를 만든다.
- [ ] PII fixture를 만든다.
- [ ] Secret fixture를 만든다.
- [ ] expected decision format을 정의한다.
- [ ] fixture runner를 Rust test와 연결한다.

### Verification

- [ ] `cargo test`에서 fixture regression 실행
- [ ] false positive/false negative 후보를 별도 목록으로 관리
- [ ] fixture에는 실제 secret이나 실제 개인정보를 넣지 않는다.

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

- Stage 6 Regression Fixture Pack
- Stage 7B Dashboard skeleton after shared audit model is agreed

### Goal

Guard decision 결과를 원문 없이 저장한다.

### Tasks

- [ ] AuditEvent model을 정의한다.
- [ ] DB migration 도구를 선택한다.
- [ ] PostgreSQL schema를 정의한다.
- [ ] content hash 저장 방식을 구현한다.
- [ ] redacted summary 저장 방식을 구현한다.
- [ ] violation detail 저장 방식을 구현한다.
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

- MVP release candidate 전 `main`에 merge한다.

---

## Stage 8. MVP Hardening and Release Candidate

### Branch

`chore/mvp-hardening`

### Must Start After

- Stage 5 is merged into `main`.
- Stage 6 is merged into `main`.
- Stage 7A is merged into `main`.
- Stage 7B is merged into `main`.

### Worktree

Not required unless documentation/testing fixes are parallelized.

### Goal

MVP 구현을 실제 실행 가능한 상태로 고정한다.

### Tasks

- [ ] root-level build/test command를 정리한다.
- [ ] Kotlin build/test를 실행한다.
- [ ] Rust build/test를 실행한다.
- [ ] integration test를 실행한다.
- [ ] regression fixture test를 실행한다.
- [ ] local run guide를 코드 주석 또는 계획 문서에 반영한다.
- [ ] known limitations를 계획 문서에 반영한다.
- [ ] 다음 확장 phase 시작 조건을 정리한다.

### Verification

- [ ] 전체 Kotlin test 통과
- [ ] 전체 Rust test 통과
- [ ] `/guard/check` e2e 통과
- [ ] audit 원문 저장 금지 테스트 통과
- [ ] dashboard read-only 조회 통과

### Merge Gate

- 모든 검증 통과 후 `main`에 merge한다.
- 이후 MVP tag 후보를 만든다.

---

## Parallel Execution Plan

### Parallel Window A: After Stage 2 Merge

```text
main
 ├─ core/rust-guard-pipeline       worktree: ../uai-bg-rust-core
 ├─ gateway/spring-skeleton        worktree: ../uai-bg-kotlin-gateway
 └─ test/regression-fixtures       worktree: ../uai-bg-regression
```

Constraints:

- `test/regression-fixtures`는 `boundary-core` crate 경로가 확정된 뒤 본격 구현한다.
- contract 파일은 이 window에서 변경하지 않는다.

### Parallel Window B: After Stage 4C Merge

```text
main
 ├─ test/guard-check-e2e           worktree: ../uai-bg-regression
 └─ audit/persistence-mvp          worktree: ../uai-bg-audit
```

Constraints:

- Dashboard는 AuditEvent model이 merge된 뒤 시작한다.

### Parallel Window C: After Stage 7A Merge

```text
main
 ├─ audit/dashboard-readonly       worktree: ../uai-bg-audit
 └─ test/regression-fixtures       worktree: ../uai-bg-regression
```

Constraints:

- 같은 worktree path를 동시에 두 브랜치가 공유하지 않는다.
- 이미 사용 중인 worktree는 별도 path를 만든다.

## Merge Order

```text
1. chore/monorepo-scaffold
2. contract/guard-check-v1
3. core/rust-guard-pipeline
4. gateway/spring-skeleton
5. service/rust-core-grpc
6. gateway/rust-core-client
7. gateway/guard-check-api
8. test/regression-fixtures
9. test/guard-check-e2e
10. audit/persistence-mvp
11. audit/dashboard-readonly
12. chore/mvp-hardening
```

## First Implementation Slice

가장 먼저 구현할 feature branch는 다음이다.

```text
chore/monorepo-scaffold
```

이 브랜치에서는 실제 비즈니스 로직을 만들지 않고, 이후 contract와 Kotlin/Rust 구현이 충돌 없이 들어갈 구조만 만든다.

## Definition of Done for MVP

- [ ] `/guard/check` API가 text payload를 받는다.
- [ ] Kotlin Gateway가 Rust Core Service를 호출한다.
- [ ] Rust Core가 text payload를 normalize한다.
- [ ] Rust Core가 prompt injection, PII, secret을 탐지한다.
- [ ] Rust Core가 `ALLOW`, `REDACT`, `BLOCK` 중 하나를 반환한다.
- [ ] `REDACT` 결과에는 redacted content가 포함된다.
- [ ] `BLOCK` 결과에는 evidence summary가 포함된다.
- [ ] Audit log에는 원문 민감정보가 저장되지 않는다.
- [ ] Dashboard에서 decision event를 read-only로 볼 수 있다.
- [ ] Rust/Kotlin/integration/regression test가 통과한다.

## Explicitly Out of MVP Scope

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

이 항목들은 모두 Guard Decision Pipeline의 입력 타입 확장으로 유지하며, MVP가 안정화된 뒤 별도 브랜치에서 진행한다.
