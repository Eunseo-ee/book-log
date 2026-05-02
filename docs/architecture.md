# 아키텍처 분석

---

## 1. 레이어드 아키텍처 다이어그램

```
[ 클라이언트 (브라우저 / 앱) ]
           │  HTTP Request
           ▼
┌──────────────────────────────────────────────┐
│              Security Layer                  │
│  JwtAuthenticationFilter                     │
│  → Bearer 토큰 추출 → 서명 검증              │
│  → SecurityContextHolder에 Authentication 저장│
└──────────────────────┬───────────────────────┘
                       │
           ▼
┌──────────────────────────────────────────────┐
│            Controller Layer                  │
│                                              │
│  AuthController    ← POST /api/auth/**       │
│  ContentController ← GET/POST /api/content   │
│  HighlightController← /api/highlight         │
│  SearchController  ← GET /api/search         │
│  CalendarController← GET /api/calendar       │
│  StatisticsController← GET /api/statistics   │
│                                              │
│  역할: HTTP 요청/응답 처리, @Valid 검증,      │
│        DTO ↔ Service 인터페이스 역할          │
└──────────────────────┬───────────────────────┘
                       │  DTO
           ▼
┌──────────────────────────────────────────────┐
│             Service Layer                    │
│                                              │
│  AuthService       → 회원가입, 로그인         │
│  ContentService    → 콘텐츠 저장/조회/필터링  │
│  HighlightService  → 하이라이트 CRUD          │
│  SearchService     → 외부 API 통합 검색       │
│  ├── KakaoSearchService  (도서)               │
│  ├── TmdbSearchService   (영화/TV/애니)        │
│  └── AnimeSearchService  (애니 상세)          │
│  CalendarService   → 월별 활성 날짜           │
│  StatisticsService → 월별 통계 집계           │
│                                              │
│  역할: 비즈니스 로직, 트랜잭션 관리,          │
│        도메인 규칙 적용                        │
└──────────────────────┬───────────────────────┘
                       │  Entity
           ▼
┌──────────────────────────────────────────────┐
│            Repository Layer                  │
│                                              │
│  ContentRepository  → JpaRepository<Content> │
│  HighlightRepository→ JpaRepository<Highlight│
│  MemberRepository   → JpaRepository<Member>  │
│                                              │
│  역할: DB CRUD, JPQL/Native 쿼리 정의         │
└──────────────────────┬───────────────────────┘
                       │  SQL
           ▼
┌──────────────────────────────────────────────┐
│          Database (PostgreSQL / Supabase)    │
│                                              │
│  tables: content, highlight, members         │
└──────────────────────────────────────────────┘

외부 API (별도 흐름)
  SearchService → KakaoSearchService → Kakao API
                → TmdbSearchService  → TMDB API
                → AnimeSearchService → Jikan API
```

---

## 2. 내가 선택한 구조의 장단점

### 채택 구조: 레이어드 아키텍처 (Layered Architecture)

각 계층이 명확히 분리되고, 상위 계층만 하위 계층에 의존하는 단방향 의존성 구조.

---

### 장점

**1. 낮은 학습 곡선**
Spring Boot의 공식 가이드와 대부분의 예제가 이 구조를 따르므로
새로운 팀원이 합류하거나 코드를 처음 보는 사람이 구조를 빠르게 파악 가능.
`@Controller` → `@Service` → `@Repository` 레이어 역할이 직관적.

**2. 책임 분리가 명확**
- Controller: 입력/출력 변환, HTTP 관심사
- Service: 비즈니스 로직, 트랜잭션
- Repository: 데이터 접근

각 계층의 역할이 명확해서 버그 발생 시 어느 계층에서 문제가 생겼는지 좁히기 쉬움.

**3. 테스트 용이성**
계층이 분리되어 있어 각 계층을 독립적으로 테스트 가능.
- Service 테스트: Repository를 Mock으로 교체
- Controller 테스트: Service를 Mock으로 교체 (MockMvc)

**4. 단순한 도메인에 적합**
이 프로젝트처럼 콘텐츠 기록, 하이라이트 관리, 검색의 비교적 단순한 도메인에서는
레이어드 아키텍처로 충분한 구조화가 가능.

---

### 단점

**1. 비즈니스 로직이 분산될 수 있음**
엔티티는 단순 데이터 컨테이너(Anemic Domain Model)가 되고,
비즈니스 로직이 Service 계층에 모두 집중되는 경향이 있음.
도메인이 복잡해질수록 Service 클래스가 비대해짐.

**2. DB 변경 시 파급 효과**
Repository가 JPA Entity와 강결합되어 있어
DB 기술을 바꾸거나(예: JPA → MyBatis) ORM 로직을 교체하면
Service까지 영향을 받을 수 있음.

**3. 계층 간 DTO 변환 반복**
Controller DTO → Service 처리 → Entity → Repository → Entity → DTO 응답
중간에 변환 코드가 여러 곳에 분산되고 보일러플레이트 코드가 늘어남.

---

## 3. 대안 구조와의 비교

### 대안 1: 헥사고날 아키텍처 (Ports and Adapters)

```
                 ┌─────────────────────────────┐
  REST API  ──── │  Inbound Port (UseCase)      │
  (Adapter)      │                              │
                 │    Application Core          │
  Kakao API ──── │    (Domain + Service)        │
  (Adapter)      │                              │
                 │  Outbound Port (Repository)  │
  PostgreSQL ──── │                              │
  (Adapter)      └─────────────────────────────┘
```

**핵심 아이디어**: 도메인 로직이 인프라(DB, HTTP)에 의존하지 않음.
인프라는 인터페이스(Port)를 통해 도메인과 통신.

**장점:**
- DB를 JPA에서 다른 기술로 교체해도 도메인 코드 무변경
- 외부 API 교체(Kakao → 다른 검색 API)가 유연
- 단위 테스트 시 DB 없이도 도메인 로직만 테스트 가능

**단점:**
- 보일러플레이트가 많음: Port 인터페이스, Adapter 구현체 별도 작성 필요
- 소규모 팀/프로젝트에서는 오버엔지니어링

**이 프로젝트에서 안 쓴 이유:**
콘텐츠 기록 앱의 도메인이 충분히 단순하고, DB와 외부 API 기술 교체 가능성이 낮음.
레이어드 아키텍처로도 구조 분리가 충분히 되어 있고,
포트폴리오 프로젝트에서 헥사고날을 완벽하게 구현하는 것은 일정 대비 이득이 크지 않음.

---

### 대안 2: CQRS (Command Query Responsibility Segregation)

```
  [Write 모델]                [Read 모델]
  Command → Aggregate         Query → ReadModel
  (상태 변경)                  (조회 최적화)
      │                            │
  WriteDB (PostgreSQL) ──── ReadDB (Redis / ES)
```

**핵심 아이디어**: 쓰기(Command)와 읽기(Query)의 책임을 분리.

**장점:**
- 조회 성능 최적화: 읽기 모델을 조회에 맞게 비정규화
- 쓰기와 읽기 부하를 독립적으로 스케일링 가능

**단점:**
- 이벤트 싱크, 최종 일관성 등 복잡도가 크게 높아짐
- 소규모 프로젝트에서 불필요한 복잡성

**이 프로젝트에서 안 쓴 이유:**
통계/캘린더 조회는 최적화가 필요하지만, CQRS까지 도입할 규모가 아님.
ContentRepository의 `@Query` 메서드 수준의 쿼리 최적화로 충분.

---

### 현재 구조 선택 요약

| 기준 | 레이어드 (선택) | 헥사고날 | CQRS |
|---|---|---|---|
| 구현 복잡도 | 낮음 | 높음 | 매우 높음 |
| 팀 학습 비용 | 낮음 | 중간 | 높음 |
| 도메인 복잡도 대응 | 보통 | 좋음 | 매우 좋음 |
| 인프라 교체 유연성 | 낮음 | 높음 | 높음 |
| 이 프로젝트 적합도 | **높음** | 중간 | 낮음 |

> 현재 도메인 규모와 팀 상황에서는 레이어드가 가장 효율적.
> 도메인이 복잡해지고 팀이 성장하면 서비스를 도메인 단위로 모듈화하거나
> 핵심 도메인 로직은 헥사고날 방식으로 리팩터링하는 것을 고려할 수 있음.
