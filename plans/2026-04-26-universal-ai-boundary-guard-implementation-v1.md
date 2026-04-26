# Universal AI Boundary Guard Implementation Plan

## Objective

이 프로젝트의 목표는 **Provider-agnostic AI Guard Gateway**를 구현하는 것이다.

핵심은 여러 AI provider 앞단에서 요청을 정규화하고, text payload 기준 정책 평가를 수행해 `ALLOW`, `REDACT`, `BLOCK` 결정을 반환하며, 그 결과를 감사 가능하게 기록하는 것이다.

이번 계획에서는 범위를 다음 3계층으로 재정리한다.

1. **MVP 본체**: Gateway + Policy Decision + Audit
2. **확장 모듈**: Permission-aware Context Filter, High-Risk Action Review
3. **검증 도구**: Regression / Eval Pack

즉, 모든 기능을 한 번에 묶지 않고, **하나의 Guard Decision Pipeline을 본체로 두고 나머지는 모듈로 확장**한다.

## Why This Structure

기존 아이디어를 모두 동시에 묶으면 비전은 커지지만, 구매자·실행 위치·지연 시간 요구가 달라져 MVP 초점이 흐려진다.

따라서 이 계획은 다음 원칙을 따른다.

- 본체는 **inline runtime guard** 문제에만 집중한다.
- context filtering은 retrieval 이후 prompt 구성 직전의 **선택 확장 모듈**로 둔다.
- action review는 tool/action 실행 직전의 **선택 확장 모듈**로 둔다.
- eval/regression은 runtime 본체가 아니라 **검증 도구**로 분리한다.

## Product Positioning

### Core Product

**Provider-agnostic AI Guard Gateway**

- 단일 AI ingress
- provider abstraction
- payload normalize / detect / decide
- audit logging

### Optional Extension Modules

1. **Permission-aware Context Filter**
   - context candidate를 principal / tenant / sensitivity / source 기준으로 필터링
2. **High-Risk Action Review**
   - tool call, service call, command execution 같은 action을 review 대상으로 승격

### Verification Tooling

1. **Regression / Eval Pack**
   - detector fixture
   - prompt injection regression
   - pii/secret regression
   - normalization bypass regression
   - provider request matrix

## Benchmark-Informed Design Choices

### 1. MVP 본체에 반영할 부분

Cloudflare AI Gateway, Portkey, Kong AI Gateway에서 가져올 부분은 아래다.

- 모든 LLM 호출의 단일 ingress
- provider별 차이를 숨기는 adapter 계층
- provider / model / request metadata 중심의 observability
- gateway를 policy enforcement point로 다루는 관점

이 프로젝트에서는 Kotlin Gateway가 이 역할을 담당한다.

### 2. 확장 모듈에 반영할 부분

Microsoft 365 Copilot, Glean, Toss PANDA에서 가져올 부분은 아래다.

- 권한 없는 context가 모델 입력에 들어가기 전에 제거되는 security trimming
- source permission inheritance
- sensitivity 기반 허용 범위 제한

Claude Code와 OpenAI agent safety에서 가져올 부분은 아래다.

- 모델 판단과 실제 실행 허용의 분리
- high-risk action의 명시적 review / approval 흐름
- deterministic policy layer

### 3. 검증 도구에 반영할 부분

PyRIT, promptfoo, garak에서 가져올 부분은 아래다.

- fixture 기반 regression
- 정책 우회 케이스 지속 검증
- provider별 입력 매트릭스
- 빌드/CI 단계의 회귀 확인

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
- Boundary Guard는 LLM/Agent로 들어가거나 나가는 payload를 실행 전 또는 전달 전 검사한다.
- Boundary Guard는 enterprise search나 knowledge graph 자체를 구축하지 않는다.
- 본체는 text payload guard와 audit에 집중한다.
- context filter와 action review는 본체가 아니라 확장 모듈이다.
- eval/regression은 runtime 의사결정 로직이 아니라 검증 도구다.
- 원문 민감정보는 audit log에 저장하지 않는다.

## Guard Decision Pipeline

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
Redact / Block
  ↓
Audit
```

MVP에서는 **text payload 기반 `/guard/check` API 하나**에 집중한다.

## Decision Types

### MVP Decision Types

- `ALLOW`: 정책상 허용
- `REDACT`: 민감정보 마스킹 후 허용 가능
- `BLOCK`: 차단

### Extension Decision Types

- `REVIEW`: 사람 검토 필요
- `QUARANTINE`: 파일/압축/문서 등 격리 후 추가 분석 필요

## Architecture Overview

```text
Client App / AI Service / Agent Runtime
                ↓
      Kotlin Guard Gateway
                ↓
        Rust Guard Core
                ↓
          Decision Result
                ↓
    Audit Log / Dashboard / Policy Hit
```

확장 모듈은 본체 경로 옆에 붙는다.

```text
                       ┌──────────────────────────────┐
                       │ Regression / Eval Pack       │
                       └──────────────────────────────┘

Client App
   ↓
Kotlin Guard Gateway
   ├─ Core Guard Path
   │    └─ Rust Guard Core
   ├─ Optional Context Filter Module
   └─ Optional Action Review Module
```

## MVP 본체

### Core Value

MVP 본체의 가치는 아래 4개다.

1. **Single AI Ingress**
2. **Provider Abstraction**
3. **Payload Policy Decision**
4. **Audit Logging**

### MVP Scope

- [ ] `GuardCheckRequest` protobuf contract를 정의한다.
- [ ] `GuardCheckResult` protobuf contract를 정의한다.
- [ ] `DecisionType` MVP 범위를 `ALLOW`, `REDACT`, `BLOCK`으로 제한한다.
- [ ] `ProviderType` MVP 범위를 `ANTHROPIC`, `OPENAI_COMPATIBLE`, `GOOGLE`, `INTERNAL`로 제한한다.
- [ ] `PayloadType` MVP 범위를 `TEXT`, `PROMPT`, `RESPONSE`, `DATA_EGRESS`로 제한한다.
- [ ] Rust Core에서 text payload 검사 pipeline을 구현한다.
- [ ] Base64, URL encoding, HTML entity, Unicode normalization을 구현한다.
- [ ] Prompt Injection Detector를 구현한다.
- [ ] PII Detector를 구현한다.
- [ ] Secret Detector를 구현한다.
- [ ] redactor를 구현한다.
- [ ] `ALLOW`, `REDACT`, `BLOCK` decision 생성 로직을 구현한다.
- [ ] provider metadata와 principal metadata를 contract에 포함한다.
- [ ] Kotlin Gateway에서 `/guard/check` API를 제공한다.
- [ ] Kotlin Gateway가 Rust Core gRPC client를 통해 검사를 요청한다.
- [ ] Kotlin Gateway에 provider adapter abstraction을 도입한다.
- [ ] Kotlin Gateway가 decision 결과를 audit log에 저장한다.
- [ ] Thymeleaf dashboard에서 guard decision 결과를 조회한다.

### MVP Phase 1. Contract First Design

- [ ] `GuardCheckRequest`를 정의한다.
- [ ] `GuardCheckResult`를 정의한다.
- [ ] `PrincipalContext`를 정의한다.
- [ ] `ProviderMetadata`를 정의한다.
- [ ] `ViolationEvidence`를 정의한다.
- [ ] `RedactionResult`를 정의한다.
- [ ] `CoreHealthCheck`를 정의한다.

### MVP Phase 2. Rust Guard Core

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

### MVP Phase 3. Kotlin Spring Gateway

- [ ] `/guard/check` API endpoint를 정의한다.
- [ ] Controller는 요청/응답 변환만 담당하도록 제한한다.
- [ ] GuardCheck Application Service를 설계한다.
- [ ] Rust Core Client를 별도 component로 분리한다.
- [ ] Guard Decision Handler를 설계한다.
- [ ] provider adapter abstraction을 설계한다.
- [ ] provider/model metadata 전달 방식을 정의한다.
- [ ] API key 기반 client 인증을 설계한다.
- [ ] Rust Core gRPC timeout, retry, error mapping을 설계한다.
- [ ] fail-closed / fail-open 정책을 설정 가능하게 설계한다.

### MVP Phase 4. Policy & Audit

- [ ] 정책 저장 모델을 정의한다.
- [ ] policy revision 개념을 도입한다.
- [ ] Kotlin은 정책 저장과 조회를 담당하고 Rust는 validation/evaluation을 담당한다.
- [ ] 정책 저장 전 Rust validation 흐름을 설계한다.
- [ ] 정책 변경 시 Rust Core sync 흐름을 설계한다.
- [ ] provider allowlist / denylist 정책을 정의한다.
- [ ] Audit Event 모델을 정의한다.
- [ ] 민감정보 원문 저장 금지 원칙을 적용한다.
- [ ] content hash와 redacted summary 저장 방식을 정의한다.
- [ ] violation detail 저장 구조를 정의한다.
- [ ] provider, model, principal, payload type, decision을 저장한다.
- [ ] core latency를 저장한다.
- [ ] audit log retention 정책을 정의한다.
- [ ] Thymeleaf dashboard에 request count, block count, redact count, policy hit, core health를 표시한다.

## 확장 모듈 A. Permission-aware Context Filter

### Module Goal

이 모듈은 retrieval 시스템을 직접 만들지 않고, 상위 시스템이 넘긴 `context candidate`를 **prompt 구성 직전** 필터링한다.

### Included Responsibilities

- principal 기준 필터링
- tenant 기준 필터링
- sensitivity 기준 필터링
- source metadata 기반 security trimming
- filtered count audit 저장

### Out of Scope

- enterprise search 구축
- knowledge graph 구축
- 원본 문서 저장소 ACL 동기화 전체 구현

### Module Scope

- [ ] `ContextCandidate`를 정의한다.
- [ ] `ContextFilterResult`를 정의한다.
- [ ] principal, tenant, sensitivity, source metadata 기반 필터 규칙을 정의한다.
- [ ] Gateway에 context filtering hook을 설계한다.
- [ ] context candidate count와 filtered count를 audit에 저장한다.
- [ ] permission-aware context filter 테스트를 구성한다.

## 확장 모듈 B. High-Risk Action Review

### Module Goal

이 모듈은 모델의 출력이나 tool intent가 곧바로 실행으로 이어지지 않도록, 고위험 action을 별도 review 대상으로 승격한다.

### Included Responsibilities

- action type taxonomy
- action risk model
- tool call / service call / command execution 검사
- high-risk action의 `REVIEW` decision 도입

### Out of Scope

- full sandbox runtime 구현
- 실제 IDE/CLI agent 전체 제어
- 모든 tool ecosystem 호환

### Module Scope

- [ ] `ActionContext`를 정의한다.
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

## 검증 도구. Regression / Eval Pack

### Tooling Goal

이 도구는 runtime 본체와 별도로 정책/탐지/정규화 우회가 회귀하지 않는지 지속 검증한다.

### Tooling Scope

- [ ] detector fixture 분류 체계를 정의한다.
- [ ] prompt injection regression fixture를 구성한다.
- [ ] pii/secret regression fixture를 구성한다.
- [ ] normalization 우회 fixture를 구성한다.
- [ ] provider별 request fixture를 구성한다.
- [ ] regression 실행 결과를 build 단계에서 확인할 수 있게 한다.

## Future Roadmap After Core Extensions

아래 항목은 현재 계획의 핵심 범위가 아니라, 본체와 2개 확장 모듈이 안정화된 뒤 검토한다.

- file attachment 입력 타입
- zip/tar 기본 검사
- archive bomb 제한
- password-protected archive 격리
- `QUARANTINE` decision 활성화
- PDF/docx/html/md text extraction
- nested archive 검사
- macro-enabled document 탐지
- YARA/ClamAV 연동
- OCR 기반 이미지 검사
- sandbox detonation 연동
- MCP tool schema integrity 검사
- tool rug-pull detection
- SIEM/SOAR 연동
- APT-style dashboard

## Testing Strategy

### MVP Core Tests

- [ ] Rust policy engine 단위 테스트를 구성한다.
- [ ] Rust detector별 fixture 테스트를 구성한다.
- [ ] prompt injection 우회 케이스 테스트를 구성한다.
- [ ] base64/url/html/unicode normalization 테스트를 구성한다.
- [ ] provider adapter fixture 테스트를 구성한다.
- [ ] redaction 테스트를 구성한다.
- [ ] Kotlin RustCoreClient integration test를 구성한다.
- [ ] `/guard/check` Gateway end-to-end flow 테스트를 구성한다.
- [ ] PostgreSQL 연동 테스트는 Testcontainers로 구성한다.

### Extension Module Tests

- [ ] permission-aware context filter 테스트를 구성한다.
- [ ] action context 검사 fixture 테스트를 구성한다.
- [ ] code change / command execution 위험 패턴 테스트를 구성한다.

### Verification Tool Tests

- [ ] detector fixture pack 실행 스크립트를 구성한다.
- [ ] provider matrix regression 실행 방식을 정의한다.
- [ ] CI에서 regression fail 기준을 정의한다.

## Delivery Priority

### Priority 1. MVP 본체

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

### Priority 2. 확장 모듈

- [ ] Permission-aware context candidate filter
- [ ] context candidate count / filtered count audit
- [ ] Action context taxonomy
- [ ] Tool / service / command intent 검사
- [ ] `REVIEW` decision 도입

### Priority 3. 검증 도구

- [ ] detector fixture 체계
- [ ] prompt injection regression pack
- [ ] pii/secret regression pack
- [ ] normalization bypass regression pack
- [ ] provider request matrix
- [ ] build / CI integration

### Priority 4. 장기 로드맵

- [ ] File / archive guard
- [ ] document extraction
- [ ] advanced threat detection
- [ ] sandbox / AV / SIEM integration
- [ ] MCP schema integrity / rug-pull detection

## Verification Criteria

- [ ] 계획 상단에서 제품 구조가 **MVP 본체 / 확장 모듈 / 검증 도구** 3계층으로 설명된다.
- [ ] MVP 본체가 Gateway + Policy Decision + Audit에 집중한다.
- [ ] permission-aware context filtering이 본체가 아니라 확장 모듈로 이동한다.
- [ ] high-risk action review가 본체가 아니라 확장 모듈로 이동한다.
- [ ] regression/eval이 runtime 핵심 기능이 아니라 검증 도구로 분리된다.
- [ ] MVP decision이 `ALLOW`, `REDACT`, `BLOCK`으로 제한된다.
- [ ] `REVIEW`, `QUARANTINE`이 확장 decision으로 분리된다.
- [ ] knowledge graph 구축이 범위 밖으로 유지된다.
- [ ] file/APT 관련 항목이 장기 로드맵으로 밀려난다.
- [ ] 원문 민감정보는 audit log에 저장되지 않는다.

## Short Pitch

Universal AI Boundary Guard는 여러 AI provider 앞단에서 payload를 정규화하고 정책 기반 결정을 내리는 **Provider-agnostic AI Guard Gateway**다.

MVP는 gateway, policy decision, audit에 집중하고, permission-aware context filtering과 high-risk action review는 선택 확장으로 두며, regression/eval은 별도 검증 도구로 운영한다.
