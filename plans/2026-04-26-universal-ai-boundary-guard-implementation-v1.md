# Universal AI Boundary Guard Implementation Plan

## Objective

Kotlin Spring Boot와 Rust Guard Core를 조합하여 LLM/Agent 경계에서 들어오고 나가는 payload를 검사하고, provider metadata와 context permission을 함께 고려한 정책 기반 `ALLOW`, `REDACT`, `BLOCK` 결정을 반환하는 단일 Guard Decision Pipeline을 우선 구현한다.

MVP는 단일 AI ingress, provider abstraction, permission-aware context filtering, high-risk action review 확장, regression fixture 운영이 가능한 구조를 목표로 한다.

확장 단계에서는 동일 Pipeline에 action, file attachment, archive, document, APT-style threat 검사를 입력 타입으로 추가한다.

## Primary Core Feature

이 프로젝트의 1차 핵심 기능은 `GuardCheckRequest`를 받아 Rust Core가 정책 기반 `GuardCheckResult`를 반환하고, Kotlin Gateway가 그 결정을 집행·기록하는 단일 **Guard Decision Pipeline**이다.

모든 기능은 이 Pipeline의 입력 타입 확장으로 취급한다.

```text
Guard Decision Pipeline
  ↓
Normalize
  ↓
Detect
  ↓
Score
  ↓
Decide
  ↓
Redact / Block / Review / Quarantine
  ↓
Audit
```

MVP에서는 **text payload 기반 `/guard/check` API 하나**에 집중한다.

## Confirmed Stack

- Gateway / Admin: Kotlin + Spring Boot 3
- Core Engine: Rust
- Core API: gRPC + Protocol Buffers
- Admin UI: Thymeleaf
- Persistence: PostgreSQL
- Observability: Actuator, Micrometer, OpenTelemetry
- Initial Deployment: Kotlin Gateway와 Rust Core를 별도 container로 배포

## Core Principle

- Boundary Guard는 고객 애플리케이션의 업무 DB 조회를 직접 수행하지 않는다.
- Boundary Guard는 LLM/Agent로 들어가거나 나오는 payload를 실행 전 또는 전달 전 검사한다.
- Boundary Guard는 enterprise search나 knowledge graph 자체를 구축하지 않는다. 대신 상위 시스템이 전달한 context candidate에 permission-aware filter와 provider 전송 정책을 적용한다.
- 최종 업무 실행 여부는 호출 측 애플리케이션 또는 agent runtime이 결정하지만, Guard는 정책 기반 decision과 evidence를 반환한다.
- 원문 민감정보는 audit log에 저장하지 않는다.
- Action Guard, Attachment Guard, APT Guard는 별도 핵심 기능이 아니라 Guard Decision Pipeline의 입력 타입 확장이다.

## Benchmark-Informed Design Choices

### 1. Single AI Ingress & Provider Abstraction

Cloudflare AI Gateway, Portkey, Kong AI Gateway 사례에서 가져올 부분은 **모든 LLM 호출의 단일 ingress**와 **provider별 차이를 뒤로 숨기는 adapter 계층**이다.

이 프로젝트에서는 Kotlin Gateway가 단일 ingress가 되고, Anthropic/Claude, OpenAI-compatible/Codex, Google/Gemini, internal endpoint를 provider metadata와 adapter로 추상화한다.

### 2. Permission-Aware Context Filtering

Microsoft 365 Copilot, Glean, Toss PANDA 사례에서 가져올 부분은 **권한 없는 context가 모델 입력에 들어가기 전에 제거되는 security trimming**이다.

이 프로젝트에서는 knowledge graph를 만들지 않고, 상위 시스템이 넘긴 context candidate를 대상으로 principal, tenant, sensitivity, source metadata 기준의 permission-aware filter를 적용한다.

### 3. High-Risk Action Review Hook

Claude Code와 OpenAI agent safety 사례에서 가져올 부분은 **모델의 판단과 실제 실행 허용을 분리하고, 고위험 action은 명시적으로 review/approval 흐름으로 넘기는 구조**다.

MVP decision은 `ALLOW`, `REDACT`, `BLOCK`으로 제한하되, 확장 단계에서 `REVIEW`를 도입할 수 있도록 contract와 service 경계를 미리 고려한다.

### 4. Regression & Eval-Driven Hardening

PyRIT, promptfoo, garak 사례에서 가져올 부분은 **정책/탐지/우회 케이스를 fixture와 regression pack으로 지속 검증하는 운영 방식**이다.

MVP부터 detector fixture, normalization fixture, prompt injection 우회 fixture를 운영하고, 이후 CI 기반 regression pack으로 확장한다.

## Guard Decision Types

### MVP Decision Types

- `ALLOW`: 정책상 허용
- `REDACT`: 민감정보 마스킹 후 허용 가능
- `BLOCK`: 차단

### Extension Decision Types

- `REVIEW`: 사람 검토 필요
- `QUARANTINE`: 파일/압축/문서 등 격리 후 추가 분석 필요

## MVP Scope

- [ ] `GuardCheckRequest` protobuf contract를 정의한다.
- [ ] `GuardCheckResult` protobuf contract를 정의한다.
- [ ] `DecisionType` MVP 범위를 `ALLOW`, `REDACT`, `BLOCK`으로 제한한다.
- [ ] Rust Core에서 text payload 검사 pipeline을 구현한다.
- [ ] Prompt Injection Detector를 구현한다.
- [ ] PII Detector를 구현한다.
- [ ] Secret Detector를 구현한다.
- [ ] Base64, URL encoding, HTML entity, Unicode normalization을 적용한다.
- [ ] provider metadata와 principal metadata를 contract에 포함한다.
- [ ] context candidate 입력과 permission-aware context filtering MVP 범위를 정의한다.
- [ ] Redaction 결과를 `GuardCheckResult`에 포함한다.
- [ ] Kotlin Gateway에서 `/guard/check` API를 제공한다.
- [ ] Kotlin Gateway가 Rust Core gRPC client를 통해 검사를 요청한다.
- [ ] Kotlin Gateway에 provider adapter abstraction을 도입한다.
- [ ] Kotlin Gateway가 decision 결과를 audit log에 저장한다.
- [ ] detector fixture와 regression fixture를 구성한다.
- [ ] Thymeleaf dashboard에서 guard decision 결과를 조회한다.

## Phase 1. Contract First Design

- [ ] `GuardCheckRequest` protobuf contract를 정의한다.
- [ ] `GuardCheckResult` protobuf contract를 정의한다.
- [ ] MVP `DecisionType`은 `ALLOW`, `REDACT`, `BLOCK`으로 제한한다.
- [ ] `ProviderType` MVP 범위는 `ANTHROPIC`, `OPENAI_COMPATIBLE`, `GOOGLE`, `INTERNAL`로 제한한다.
- [ ] `PayloadType` MVP 범위는 `TEXT`, `PROMPT`, `RESPONSE`, `DATA_EGRESS`로 제한한다.
- [ ] 확장용 `PayloadType` 후보로 `TOOL_CALL`, `SERVICE_CALL`, `DB_QUERY`, `CODE_CHANGE`, `COMMAND_EXECUTION`, `FILE_ATTACHMENT`를 예약한다.
- [ ] `PrincipalContext`를 정의한다.
- [ ] `ProviderMetadata`를 정의한다.
- [ ] `ContextCandidate`와 `ContextFilterResult`를 정의한다.
- [ ] `ViolationEvidence`를 정의한다.
- [ ] `RedactionResult`를 정의한다.
- [ ] `PolicyBundleSyncRequest`를 정의한다.
- [ ] `CoreHealthCheck`를 정의한다.

## Phase 2. Rust Guard Core MVP

- [ ] Rust Core를 gRPC와 분리된 순수 library로 먼저 설계한다.
- [ ] text payload normalize 단계를 설계한다.
- [ ] Base64, URL encoding, HTML entity, Unicode normalization을 구현한다.
- [ ] policy engine 입력/출력 모델을 정의한다.
- [ ] Prompt Injection Detector를 구현한다.
- [ ] PII Detector를 구현한다.
- [ ] Secret Detector를 구현한다.
- [ ] redactor를 구현한다.
- [ ] risk scoring 기준을 정의한다.
- [ ] `ALLOW`, `REDACT`, `BLOCK` decision 생성 로직을 구현한다.
- [ ] detector 실행 결과에 evidence를 포함한다.

## Phase 3. Kotlin Spring Gateway MVP

- [ ] `/guard/check` API endpoint를 정의한다.
- [ ] Controller는 요청/응답 변환만 담당하도록 제한한다.
- [ ] GuardCheck Application Service를 설계한다.
- [ ] Rust Core Client를 별도 component로 분리한다.
- [ ] Guard Decision Handler를 설계한다.
- [ ] provider adapter abstraction을 설계한다.
- [ ] provider/model metadata 전달 방식을 정의한다.
- [ ] permission-aware context filtering hook을 설계한다.
- [ ] API key 기반 client 인증을 설계한다.
- [ ] Rust Core gRPC timeout, retry, error mapping을 설계한다.
- [ ] fail-closed / fail-open 정책을 설정 가능하게 설계한다.
- [ ] audit event 저장 흐름을 설계한다.
- [ ] Thymeleaf dashboard에서 guard decision 결과를 조회할 수 있게 한다.

## Phase 4. Policy Management MVP

- [ ] 정책 저장 모델을 정의한다.
- [ ] policy revision 개념을 도입한다.
- [ ] Kotlin은 정책 저장과 조회를 담당하고 Rust는 validation/evaluation을 담당한다.
- [ ] 정책 저장 전 Rust validation 흐름을 설계한다.
- [ ] 정책 변경 시 Rust Core sync 흐름을 설계한다.
- [ ] provider allowlist / denylist 정책을 정의한다.
- [ ] principal, tenant, sensitivity 기반 context filter 정책을 정의한다.
- [ ] high-risk action review 확장 정책 자리를 예약한다.
- [ ] 초기 정책 UI는 read-only 중심으로 제한한다.

## Phase 5. Audit Logging & Monitoring MVP

- [ ] Audit Event 모델을 정의한다.
- [ ] 민감정보 원문 저장 금지 원칙을 적용한다.
- [ ] content hash와 redacted summary 저장 방식을 정의한다.
- [ ] violation detail 저장 구조를 정의한다.
- [ ] provider, model, principal, payload type, decision을 저장한다.
- [ ] context candidate count와 filtered count를 저장한다.
- [ ] core latency를 저장한다.
- [ ] audit log retention 정책을 정의한다.
- [ ] Thymeleaf dashboard에 request count, block count, redact count, policy hit, core health를 표시한다.

## Phase 6. Regression & Eval MVP

- [ ] detector fixture 분류 체계를 정의한다.
- [ ] prompt injection regression fixture를 구성한다.
- [ ] pii/secret regression fixture를 구성한다.
- [ ] normalization 우회 fixture를 구성한다.
- [ ] provider별 request fixture를 구성한다.
- [ ] regression 실행 결과를 build 단계에서 확인할 수 있게 한다.

## Extension Phase 1. Action Context Guard

Action Guard는 별도 핵심 기능이 아니라 Guard Decision Pipeline의 action 입력 타입 확장이다.

- [ ] `ActionContext`를 정의한다.
- [ ] action type taxonomy를 정의한다.
- [ ] action risk model을 정의한다.
- [ ] DB query intent를 `GuardCheckRequest`의 action context로 검사한다.
- [ ] service call intent를 `GuardCheckRequest`의 action context로 검사한다.
- [ ] tool call intent를 `GuardCheckRequest`의 action context로 검사한다.
- [ ] code change diff를 `GuardCheckRequest`의 action context로 검사한다.
- [ ] command execution request를 `GuardCheckRequest`의 action context로 검사한다.
- [ ] 민감 테이블/컬럼 정책 모델을 정의한다.
- [ ] tenant/user scope 조건 검사 정책을 설계한다.
- [ ] destructive SQL/command 탐지 규칙을 정의한다.
- [ ] Payload Egress Guard를 Action Guard와 통합한다.
- [ ] Prompt Injection 탐지를 모든 action 입력에 적용한다.
- [ ] high-risk action에 대해 `REVIEW` decision을 도입한다.

## Extension Phase 2. File Attachment Guard

Attachment Guard는 별도 핵심 기능이 아니라 Guard Decision Pipeline의 file attachment 입력 타입 확장이다.

- [ ] file attachment를 `GuardCheckRequest` 입력 타입으로 추가한다.
- [ ] Content Type Detection을 설계한다.
- [ ] Archive & Container Guard를 설계한다.
- [ ] zip/tar 기본 archive 검사를 추가한다.
- [ ] archive bomb 제한을 추가한다.
- [ ] 압축 해제 안전 제한을 정의한다.
- [ ] password-protected archive를 `BLOCK` 또는 `QUARANTINE` 처리한다.
- [ ] nested archive 검사 정책을 정의한다.
- [ ] `QUARANTINE` decision type을 활성화한다.
- [ ] 파일 검사 결과를 audit dashboard에 노출한다.

## Extension Phase 3. Advanced Threat / APT Guard

APT Guard는 별도 핵심 기능이 아니라 Guard Decision Pipeline의 고위험 입력 타입과 고급 탐지 룰셋 확장이다.

- [ ] PDF/docx/html/md text extraction을 추가한다.
- [ ] 문서 metadata/comment/hidden text 검사 범위를 정의한다.
- [ ] macro-enabled document 탐지를 추가한다.
- [ ] PDF active content 탐지 정책을 정의한다.
- [ ] SVG를 active document로 분류한다.
- [ ] OCR 기반 이미지 검사 확장을 검토한다.
- [ ] steganography 의심 탐지를 검토한다.
- [ ] YARA/ClamAV 연동을 추가한다.
- [ ] sandbox detonation 연동을 검토한다.
- [ ] MCP tool schema integrity 검사를 검토한다.
- [ ] tool rug-pull detection을 검토한다.
- [ ] SIEM/SOAR 연동을 검토한다.
- [ ] APT-style TTP dashboard를 확장한다.

## Default File Security Policy for Extension Phases

- [ ] password-protected archive는 기본 `QUARANTINE` 또는 `BLOCK`으로 처리한다.
- [ ] archive bomb 의심 파일은 `BLOCK`으로 처리한다.
- [ ] nested archive depth 초과 파일은 `QUARANTINE`으로 처리한다.
- [ ] executable/script 포함 archive는 `REVIEW` 또는 `BLOCK`으로 처리한다.
- [ ] macro-enabled Office file은 `REVIEW` 또는 `BLOCK`으로 처리한다.
- [ ] PDF active content 포함 파일은 `REVIEW` 또는 `BLOCK`으로 처리한다.
- [ ] SVG script 포함 파일은 `BLOCK`으로 처리한다.
- [ ] base64 encoded PowerShell, shell reverse connection, credential exfiltration 패턴은 `BLOCK`으로 처리한다.
- [ ] unknown binary는 `QUARANTINE`으로 처리한다.

## Testing Strategy

### MVP Tests

- [ ] Rust policy engine 단위 테스트를 구성한다.
- [ ] Rust detector별 fixture 테스트를 구성한다.
- [ ] prompt injection 우회 케이스 테스트를 구성한다.
- [ ] base64/url/html/unicode normalization 테스트를 구성한다.
- [ ] provider adapter fixture 테스트를 구성한다.
- [ ] permission-aware context filter 테스트를 구성한다.
- [ ] redaction 테스트를 구성한다.
- [ ] Kotlin RustCoreClient integration test를 구성한다.
- [ ] `/guard/check` Gateway end-to-end flow 테스트를 구성한다.
- [ ] PostgreSQL 연동 테스트는 Testcontainers로 구성한다.

### Extension Tests

- [ ] archive bomb 제한 테스트를 구성한다.
- [ ] password-protected archive 정책 테스트를 구성한다.
- [ ] nested archive 정책 테스트를 구성한다.
- [ ] document extraction fixture 테스트를 구성한다.
- [ ] action context 검사 fixture 테스트를 구성한다.
- [ ] code change / command execution 위험 패턴 테스트를 구성한다.

## MVP Priority

### Priority 1: Single Guard Decision Pipeline

- [ ] `GuardCheckRequest` contract
- [ ] `GuardCheckResult` contract
- [ ] Provider metadata / principal context
- [ ] `/guard/check` API
- [ ] Kotlin provider adapter abstraction
- [ ] Rust text payload 검사 pipeline
- [ ] Prompt Injection Guard
- [ ] PII Detector
- [ ] Secret Detector
- [ ] Base64/URL/HTML/Unicode normalization
- [ ] Redaction
- [ ] `ALLOW` / `REDACT` / `BLOCK` decision
- [ ] Audit log 원문 저장 금지
- [ ] Decision dashboard read-only 조회

### Priority 2: Pipeline Input Extension

- [ ] Data egress payload 세분화
- [ ] Permission-aware context candidate filter
- [ ] provider allowlist / denylist policy
- [ ] DB query intent 검사
- [ ] Service call intent 검사
- [ ] Tool call intent 검사
- [ ] Code Change Guard
- [ ] Command Execution Guard
- [ ] `REVIEW` decision

### Priority 3: File / APT Extension

- [ ] File attachment 입력 타입
- [ ] zip/tar 기본 검사
- [ ] archive bomb 제한
- [ ] password-protected archive 차단 또는 격리
- [ ] `QUARANTINE` decision
- [ ] PDF/docx/html/md text extraction
- [ ] nested archive 검사
- [ ] macro-enabled document 탐지
- [ ] YARA/ClamAV 연동
- [ ] OCR 기반 이미지 검사
- [ ] sandbox detonation 연동
- [ ] MCP tool schema integrity 검사
- [ ] tool rug-pull detection
- [ ] SIEM/SOAR 연동
- [ ] APT TTP dashboard

## Verification Criteria

- [ ] 계획 상단에서 핵심 기능이 `Guard Decision Pipeline` 하나로 명확히 설명된다.
- [ ] MVP 범위가 text payload 기반 `/guard/check` API로 제한된다.
- [ ] 단일 AI ingress와 provider abstraction이 계획에 반영된다.
- [ ] permission-aware context filtering이 knowledge graph 없이 적용되는 구조로 설명된다.
- [ ] Action Guard와 Attachment Guard가 핵심 기능이 아니라 Pipeline 입력 확장으로 재분류된다.
- [ ] `ALLOW`, `REDACT`, `BLOCK`이 MVP decision으로 분리된다.
- [ ] `REVIEW`, `QUARANTINE`은 확장 decision으로 분리된다.
- [ ] detector fixture와 regression pack이 MVP 검증 전략에 포함된다.
- [ ] MVP Priority 항목이 모두 체크박스로 추적 가능하다.
- [ ] Archive, OCR, Sandbox, MCP, SIEM/SOAR, APT Dashboard가 후순위 확장으로 이동된다.
- [ ] 원문 민감정보는 audit log에 저장되지 않는다.
