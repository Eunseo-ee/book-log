# 기술 스택 선택 근거

> "왜 이걸 쓰셨어요?" 질문에 대한 구체적인 답변 자료.
> 단순 나열이 아닌, 이 프로젝트의 특성과 연결된 선택 이유.

---

## 1. JWT vs 세션 방식 — JWT 선택

### 세션 방식이란?
로그인 시 서버가 세션(session)을 생성하고, 클라이언트는 세션 ID를 쿠키에 저장.
매 요청마다 서버는 세션 저장소에서 ID를 조회해 인증 처리.

### JWT 방식이란?
로그인 시 서버가 사용자 정보를 포함한 서명된 토큰을 발급.
클라이언트가 토큰을 저장하고, 매 요청에 포함해서 전송.
서버는 토큰 서명만 검증하면 되고, 저장소를 조회하지 않음.

### 비교

| 항목 | 세션 | JWT |
|---|---|---|
| 서버 상태 | Stateful (세션 저장소 필요) | Stateless (저장소 불필요) |
| 수평 확장(Scale-out) | 세션 공유 문제 발생 | 토큰만 검증하므로 자유로움 |
| 토큰 탈취 시 즉각 무효화 | 세션 삭제로 즉시 가능 | 만료 시간까지 유효 (약점) |
| 네트워크 비용 | 세션 ID만 전송 (작음) | 토큰 자체 전송 (상대적으로 큼) |
| 모바일/SPA 친화성 | 쿠키 의존적 | Authorization 헤더로 자유롭게 사용 |

### 이 프로젝트에서 JWT를 선택한 이유

**1. REST API + 프론트엔드 분리 구조**
이 프로젝트는 Spring Boot REST API와 별도 클라이언트(향후 React 등)가 분리된 구조.
세션 방식의 쿠키는 같은 도메인에 묶이는 특성이 있어 CORS 처리가 복잡해짐.
JWT는 `Authorization: Bearer {token}` 헤더 방식이라 도메인에 독립적이고 클라이언트가 직접 관리.

**2. Stateless 설계 — 서버 부하 감소**
세션 방식은 Redis 같은 별도 세션 스토리지가 없으면 서버 메모리에 세션이 쌓임.
Supabase를 쓰는 이 프로젝트에서 추가 저장소 없이 인증을 처리하려면 JWT가 적합.

**3. 모바일 확장 가능성**
책/영화/애니 기록 앱은 향후 모바일 앱으로 확장할 가능성이 있음.
모바일 환경에서는 쿠키 관리가 불편하고, Authorization 헤더 방식이 표준적.

### JWT 선택의 트레이드오프 (인지하고 선택)
- **토큰 탈취 시 즉각 무효화 불가**: access token은 15분으로 짧게 설정해 위험 최소화
- **refresh token 관리 복잡도**: 7일짜리 refresh token이 탈취되면 문제. 추후 DB 저장 + 블랙리스트 방식으로 보완 가능

---

## 2. PostgreSQL 선택 (vs MySQL)

### 선택 이유

**1. UUID 네이티브 지원**
Member 엔티티의 id를 UUID 타입으로 설계했는데,
PostgreSQL은 `uuid` 타입을 네이티브로 지원해 별도 변환 없이 저장/조회가 효율적.
MySQL도 UUID를 저장할 수 있지만 `VARCHAR(36)` 또는 `BINARY(16)`으로 우회해야 함.

**2. `EXTRACT`, `TO_CHAR` 등 날짜 함수 풍부함**
이 프로젝트의 ContentRepository에는 날짜 기반 쿼리가 많음:
```sql
-- 실제 사용 중인 쿼리
WHERE EXTRACT(YEAR FROM view_date) = :year
WHERE TO_CHAR(view_date, 'YYYY-MM') = :yearMonth
```
MySQL의 `DATE_FORMAT()`은 같은 역할을 하지만, PostgreSQL의 함수가 ANSI SQL에 더 가깝고
복잡한 날짜 연산(캘린더, 통계)에서 PostgreSQL이 더 풍부한 기능을 제공.

**3. JSONB 확장 가능성**
책/영화 API에서 받아오는 응답 데이터 구조가 다양함.
추후 비정형 메타데이터를 저장해야 할 경우 PostgreSQL의 `JSONB` 타입이 유용.
MySQL의 JSON 타입과 달리 JSONB는 인덱싱과 쿼리 성능이 월등히 좋음.

**4. Supabase가 PostgreSQL 기반**
Supabase 자체가 PostgreSQL을 기반으로 동작하므로, PostgreSQL을 선택하면
Supabase의 Row Level Security(RLS), Realtime, Storage 등의 기능과 100% 호환.

---

## 3. Supabase 선택 이유

### Supabase란?
오픈소스 Firebase 대안. PostgreSQL을 기반으로 데이터베이스, 인증, 스토리지, Realtime 기능을 제공하는 BaaS(Backend as a Service).

### 선택 이유

**1. 무료 PostgreSQL 호스팅 (개발/포트폴리오에 최적)**
로컬 Docker 없이 즉시 클라우드 PostgreSQL을 무료로 사용 가능.
포트폴리오 배포 시 DB 서버를 별도로 관리할 필요 없음.

**2. 웹 대시보드에서 DB 직접 확인 가능**
Supabase 대시보드에서 테이블 조회, SQL 직접 실행이 가능해
개발 중 데이터 확인이 매우 편리함.

**3. Connection Pooler 제공**
`aws-1-ap-northeast-1.pooler.supabase.com:6543` (PgBouncer 방식)을 통해
커넥션 풀링을 서버 측에서 처리해줌.
서버리스 환경이나 연결이 잦은 경우 DB 연결 과부하 방지에 유용.

**4. 향후 Auth, Storage 기능 확장 가능성**
현재는 JWT를 직접 구현했지만, 추후 Supabase Auth로 교체하거나
이미지 저장에 Supabase Storage를 활용하는 방향으로 확장 가능.

### 트레이드오프
- Free tier는 7일 이상 비활성 시 프로젝트가 일시정지됨 (포트폴리오 배포 시 주의)
- 국내 리전 없음 (현재 ap-northeast-1 = 도쿄 사용 중)

---

## 4. S3 이미지 서버 선택 이유

> 현재 thumbnailUrl을 외부 URL(Kakao, TMDB API 응답)로 저장하고 있으나,
> 사용자 프로필 이미지 또는 커스텀 썸네일 업로드가 필요해지면 S3를 도입할 예정.

### 로컬 파일 저장 vs S3 비교

| 항목 | 로컬 파일 저장 | AWS S3 |
|---|---|---|
| 설정 난이도 | 간단 | 복잡 (IAM, 버킷 정책) |
| 서버 재시작/배포 | 파일 유실 위험 | 영속적 보관 |
| 스케일아웃 | 서버마다 파일 공유 불가 | CDN과 연동 쉬움 |
| 비용 | 서버 디스크 용량 | 저장 및 전송량 과금 |
| URL 공유 | 직접 서버 도메인 노출 | Presigned URL로 보안 제공 |

### S3 선택 근거

**1. 서버 인프라로부터 파일 분리**
배포 시 서버를 재시작하거나 인스턴스를 바꿔도 이미지 유실 없음.
Docker 기반 배포 환경에서 로컬 파일 저장은 컨테이너 재생성 시 모두 삭제됨.

**2. CloudFront CDN과 연동**
전 세계 엣지 서버를 통해 이미지를 빠르게 제공.
책/영화 썸네일은 자주 읽히고 업데이트가 드문 정적 콘텐츠이므로 CDN 효과가 큼.

**3. Presigned URL로 직접 업로드**
클라이언트가 서버를 거치지 않고 S3에 직접 업로드 가능.
서버 트래픽 감소 + 업로드 속도 향상.

**4. 업계 표준 + 포트폴리오 어필**
실무에서 이미지/파일 저장에 S3(또는 호환 스토리지)는 사실상 표준.
이를 사용해본 경험은 채용 시 긍정적 평가 요소.
