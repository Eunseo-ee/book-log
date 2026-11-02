# 21-1. 회원가입 이메일 중복 허용 버그 수정 (task 21 후속)

- 번호: 21 수동 검증 중 발견한 후속 작업이라 `21-1`. 22는 Phase 10 성능 확인용으로 비워 둔다. 디렉터리에 `01-search-manual-test.md`처럼 번호 뒤에 접미사가 붙은 선례가 있어 파일명 규칙에 어긋나지 않는다 (정렬상 `21-1`이 `21-front-login`보다 앞에 오는 정도의 차이).
- 브랜치: `feat/auth-member` 그대로. 커밋하지 않았다. 실제 DB(Supabase) 접속·DDL 실행·데이터 삭제 없음.
- 시작 시 작업 디렉터리가 `class-schedule-generator`(다른 프로젝트)였다. 요청 내용과 `.claude/`는 `book-log-main`에만 있어 이 저장소에서 작업했다 (task 19~21과 같은 상황).
- 시작 시 `git status`는 깨끗하지 않았다 (task 21의 프론트 변경이 미커밋 상태). 이번 변경과 섞여 있으니 커밋 시 파일 단위로 나눌 것.

## 증상
회원가입 시 아이디(username)만 다르면 같은 이메일로 가입된다. task 21 수동 검증 중 발견.

## 1단계: 현상 파악 (코드 수정 전에 확인한 사실)
| 질문 | 확인 결과 (근거) |
|---|---|
| 1. 서비스에 이메일 중복 검사가 있는가 | **아예 없었다.** `AuthService.register`는 `existsByUsername`만 검사하고 `request.getEmail()`을 그대로 저장했다. `MemberRepository`에도 이메일 조회 메서드가 없었다. → 대소문자·공백 우회 이전에 검사 자체가 없는 상태 |
| 2. email 컬럼에 unique 제약이 있는가 | **없었다.** `Member.email`은 `@Column(nullable = false)`뿐. (username만 `unique = true`) |
| 3. username 중복은 어떻게 막는가 | 두 겹: ① 서비스 `existsByUsername` → `DuplicateUsernameException`(409) ② 컬럼 `unique = true` + `saveAndFlush`를 try/catch로 감싸 `DataIntegrityViolationException`을 같은 예외로 변환 (task 13 결정). 이메일도 같은 구조로 맞췄다 |
| 4. 로그인은 email/username 기준? | **username 기준** (`findByUsername`, `LoginRequestDto`는 username/password). email은 저장만 되고 `AuthService` 밖에서는 쓰이지 않는다 (`grep getEmail`) |

추가로 확인한 것
- DTO: `RegisterRequestDto.email`은 `@NotBlank @Email @Size(max=100)`. 정규화(trim/소문자)는 어디에도 없었다.
- 기존의 `catch (DataIntegrityViolationException)`은 **모든** 무결성 위반을 "아이디 중복"으로 바꿨다. 이메일 unique를 추가하면 이메일 위반이 "이미 사용 중인 아이디입니다."로 나가므로 어떤 제약이 걸렸는지 구분이 필요했다 (아래 결정 3).
- 회원가입 관련 기존 테스트 위치: 서비스 `service/AuthServiceTest`(Mockito, 가입 성공/아이디 중복/동시 가입 변환), 웹 `controller/AuthControllerTest`(`@WebMvcTest`, 400/409 응답), `controller/AuthTokenControllerTest`·`service/BcryptLongPasswordTest`(비밀번호 길이). 다른 테스트의 Member 생성은 모두 이메일이 서로 달라 새 unique 제약과 충돌하지 않았다.
- 프론트 `signup/page.tsx`는 409 메시지를 그대로 보여주도록 되어 있다 (수정 안 함, 발견 사항 참고).

## 2단계: 기존 중복 데이터 확인 (삭제 금지, 내가 직접 실행)
이미 같은 이메일(대소문자 무시)로 가입된 계정이 있으면 unique 제약 추가가 실패한다. **아래 SQL은 Supabase SQL 편집기에서 직접 실행** (조회만, 이번 작업에서 실행하지 않았다).

```sql
-- (1) 정규화 기준 중복 이메일 그룹. 결과가 0행이어야 제약을 걸 수 있다
SELECT lower(trim(email)) AS normalized_email,
       count(*)           AS cnt,
       array_agg(id ORDER BY id)       AS member_ids,
       array_agg(username ORDER BY id) AS usernames
FROM members
GROUP BY lower(trim(email))
HAVING count(*) > 1;

-- (2) 정규화되지 않은 기존 행 (대문자/앞뒤 공백 포함). 새 가입은 항상 소문자·trim 저장이라
--     이 행들이 남아 있으면 existsByEmail이 못 찾는다
SELECT id, username, email FROM members WHERE email <> lower(trim(email));
```

정리가 필요하면 순서 (앱이 제약을 만들기 **전에**):
1. (1)의 중복 그룹마다 남길 계정을 정하고, 나머지 계정은 직접 정리(삭제/이메일 변경). 그 계정의 `content`가 FK로 걸려 있으니 데이터 이전 또는 삭제 여부를 먼저 결정한다.
2. (1)이 0행인 것을 확인한 뒤 정규화: `UPDATE members SET email = lower(trim(email)) WHERE email <> lower(trim(email));`
3. 그다음 앱을 재기동한다 (`ddl-auto: update`가 제약을 생성). 또는 직접: `ALTER TABLE members ADD CONSTRAINT uk_members_email UNIQUE (email);`

## 제약이 실제로 걸렸는지 확인 (내가 DB에서 직접)
`ddl-auto: update`는 중복 데이터가 있으면 제약 생성 실패를 로그만 남기고(Hibernate 기본 전략이 "제약 삭제/재생성을 조용히 시도"인 것으로 알고 있으나 이 버전에서 직접 확인하지는 않았다) 앱은 정상 기동한다. 그래서 반드시 DB에서 확인한다.

```sql
-- 이메일 unique 제약이 있는지 (1행이 나와야 한다: uk_members_email, UNIQUE (email))
SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'members'::regclass AND contype = 'u';

-- 제약이 인덱스로도 만들어졌는지
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'members';
```
- 제약 이름이 `uk_members_email`이 아니거나 없으면 중복 데이터가 남아 생성이 실패한 것이다. 기동 로그에서 `uk_members_email`이 들어간 WARN/ERROR도 찾아본다.
- 이름이 코드와 달라지면 동시 가입 시 이메일 위반을 아이디 중복으로 잘못 알리므로(결정 3), 이름은 꼭 `uk_members_email`이어야 한다.

## 수동 검증 절차 (아직 실행하지 않음)
H2 테스트는 H2에서만 증명한다. 실제 PostgreSQL에서의 제약 이름 추출은 아래로 확인한다 (Testcontainers 테스트는 Docker 비호환으로 skip, task 20 발견 2번).
1. 정리·제약 확인(위) 후 백엔드 기동.
2. 같은 이메일로 아이디만 다르게 두 번 가입 → 두 번째가 409 `이미 사용 중인 이메일입니다.`
3. `Alice@Example.com` 가입 후 ` alice@example.com `(공백)·`ALICE@EXAMPLE.COM` 재가입 → 409. (DTO `@Email` 검증이 공백 포함 입력을 먼저 400으로 막을 수 있다. 그 경우 대소문자 케이스만 확인)
4. 가입된 이메일이 DB에 소문자로 저장됐는지 `SELECT email FROM members`.
5. (선택) 같은 이메일 요청 2개를 동시에 전송(`curl ... & curl ... &`) → 하나는 201, 하나는 409(이메일). 500이면 PostgreSQL에서 제약 이름 추출이 안 되는 것이니 기록해 달라.

## 변경한 파일
| 파일 | 내용 |
|---|---|
| `domain/Member.java` | `@Table(uniqueConstraints = uk_members_email)` 추가, 제약 이름 상수 `EMAIL_UNIQUE_CONSTRAINT` |
| `repository/MemberRepository.java` | `existsByEmail` 추가 |
| `service/AuthService.java` | 이메일 정규화(trim + `Locale.ROOT` 소문자), 가입 전 `existsByEmail` 검사, 저장 시 `DataIntegrityViolationException`을 제약 이름으로 구분해 이메일/아이디 중복으로 변환 |
| `exception/DuplicateEmailException.java` (신규) | "이미 사용 중인 이메일입니다." |
| `exception/GlobalExceptionHandler.java` | `DuplicateEmailException` → 409 (`DuplicateUsernameException`과 같은 형식) |
| `test/.../service/AuthServiceTest.java` | 6개 추가 |
| `test/.../controller/AuthControllerTest.java` | 이메일 중복 409 응답 1개 추가 |
| `test/.../repository/MemberEmailUniqueRepositoryTest.java` (신규) | 임베디드 H2로 unique 제약 + 제약 이름 검증 2개 |
| `.claude/decisions.md` | 항목 추가만 |

변경하지 않음: 프론트 전체, 로그인/토큰 로직, 의존성(`build.gradle`), `application*.yml`, 기존 테스트의 기존 케이스.

## 결정 사항
1. **정규화**: 저장·비교 전에 `trim().toLowerCase(Locale.ROOT)`. `Locale.ROOT`는 터키어 로케일 등에서 `I` 변환이 달라지는 문제를 피한다. 이메일 로컬파트는 RFC상 대소문자 구분이 가능하지만 실무에서 구분하는 곳이 거의 없어 소문자 통일이 안전하다고 판단.
2. **검사 + 제약 둘 다**: `existsByEmail`은 친절한 메시지용, `uk_members_email`은 최종 방어선. 검사만 있으면 동시 요청 둘이 모두 검사를 통과한다 (check-then-act race).
3. **제약 구분은 이름으로**: 이메일 unique를 추가하면 가입 INSERT가 깨지는 이유가 둘(아이디/이메일)이 된다. 기존처럼 전부 아이디 중복으로 바꾸면 이메일 위반이 틀린 메시지가 된다. Hibernate의 `ConstraintViolationException.getConstraintName()`이 `uk_members_email`인지 대소문자 무시로 비교하고, 아니면 기존대로 아이디 중복으로 본다. `ContentService`가 쓰는 SQLState(23505) 방식은 "유니크 위반인지"만 알려주고 "어느 제약인지"는 몰라서 여기서는 쓰지 못했다. 메시지 문자열에 `email`이 있는지 보는 방법은 아이디가 `email`인 사용자의 `Key (username)=(email)` 메시지를 잘못 판정해서 쓰지 않았고, 이를 테스트로 고정했다.
4. **응답은 409**: 아이디 중복과 같은 `ErrorResponse` 형식, 별도 예외 클래스(task 13 결정과 동일한 이유로 `IllegalStateException` 재사용 안 함).
5. **프론트·로그인·토큰은 건드리지 않음**.

## 발견한 문제 (고치지 않고 기록만)
1. **이메일 존재 여부가 노출된다.** 이메일 중복 409는 "이 이메일은 가입되어 있다"를 알려 계정 열거에 쓰일 수 있다 (로그인은 task 13에서 일부러 구분하지 않게 했다). 가입 화면에서는 어쩔 수 없는 트레이드오프이고, 막으려면 이메일 인증 방식이 필요하다. 백로그 후보.
2. **기존 데이터가 정규화돼 있지 않으면 `existsByEmail`이 못 찾는다.** 위 2단계의 (2)와 UPDATE를 먼저 해야 한다. (코드가 아니라 데이터 정리 문제)
3. **PostgreSQL에서의 제약 이름 추출은 실제로 확인하지 못했다.** H2 테스트만 통과. Testcontainers PostgreSQL 테스트가 skip되는 상태(task 20 발견 2번)라서 위 수동 검증 5번으로 확인해야 한다. 이름 추출이 실패하면 동시 가입 이메일 충돌이 "아이디 중복" 메시지로 나간다(500은 아님).
4. **DTO `@Email`이 앞뒤 공백을 먼저 400으로 막을 가능성이 높다**(Hibernate Validator 이메일 정규식이 공백을 허용하지 않는 것으로 알고 있으나 이번에 실행해 확인하지는 않았다). 그렇다면 서비스의 trim은 방어용이고, 공백이 포함된 요청의 사용자 경험은 "형식 오류"다. 서비스 단위 테스트는 서비스에 직접 호출해서 검증했다.
5. **프론트가 409 이메일 메시지를 필드가 아니라 일반 에러로 보여준다.** `signup/page.tsx`는 409 `message`를 그대로 표시한다고 주석에 쓰여 있다. 화면에서의 실제 표시는 확인하지 않았다 (수정 범위 밖).
6. **아이디 대소문자 정책은 여전히 미정.** `Alice`와 `alice`가 다른 계정이다 (백로그 13번 항목 "아이디 대소문자 구분 정책 미정"과 같음). 이메일은 이번에 정리했지만 아이디는 그대로.
7. 백로그의 "이메일 중복 허용" 문구(13번 항목)는 이번에 해결됐다. 기존 항목은 수정하지 않았으니 필요하면 직접 정리할 것.
8. 가입 시 이메일은 저장만 되고 어디에도 쓰이지 않는다 (인증·비밀번호 찾기 없음). unique를 건 이상 사용자가 이메일을 바꿀 수단이 없어, 이후 프로필 수정 기능이 생기면 같은 정규화·중복 검사를 거쳐야 한다.

## 테스트 결과
`./gradlew test` (실제 실행): **BUILD SUCCESSFUL, 223개 실행, 실패 0, 에러 0, skip 9.** 이전 214개 + 신규 9개(서비스 6, 컨트롤러 1, H2 리포지토리 2).
- skip 9는 전부 `ContentRepositoryPostgresTest` (기존 기록, task 20 발견 2번). 새로 실패하거나 새로 skip된 테스트 없음.
- 신규 서비스 테스트: 이미 가입된 이메일 거절 / 대소문자만 다른 이메일 거절 / 앞뒤 공백 이메일 거절 / 정규화되어 저장 / 이메일 제약 위반 → 중복 이메일 예외 / 다른 제약 위반 → 아이디 중복 유지(아이디가 `email`인 경우 포함).
- `MemberEmailUniqueRepositoryTest`는 임베디드 H2(`@DataJpaTest` 기본)로 돌아 Supabase에 붙지 않는다. 같은 이메일 저장이 DB 레벨에서 거부되고 위반 제약 이름이 `uk_members_email`임을 확인.
- 정상 가입 성공 케이스는 기존 `registerStoresHash`가 커버하고, 신규 `registerStoresNormalizedEmail`이 정규화 저장을 확인한다.
- 기존 HighlightRepositoryTest의 실제 DB 접속 문제는 이미 기록된 상태라 건드리지 않았다 (이번 실행의 로그에서 새 접속 오류는 없었음).

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 회원가입에서 아이디만 다르면 같은 이메일로 가입됐다. 서비스에 이메일 검사도, DB 제약도 없었다.
2. **고려한 선택지**: ① 서비스 `existsByEmail`만 ② DB unique 제약만 ③ 둘 다 (+ 저장 전 정규화).
3. **선택 이유(트레이드오프)**: ③. ①만 하면 동시 요청 두 개가 둘 다 "없음"을 보고 통과하는 race condition이 남고, ②만 하면 사용자가 "500/알 수 없는 오류"를 본다. 제약이 최종 방어선, 검사는 친절한 메시지용. 정규화를 하지 않으면 `A@x.com`과 `a@x.com`이 서로 다른 값이라 검사와 제약을 모두 통과한다. 비용: 기존 데이터를 먼저 정리해야 제약을 걸 수 있고(`ddl-auto: update`는 실패를 조용히 넘길 수 있음), 이메일 존재 여부가 가입 응답으로 드러난다.
4. **막혔던 부분과 해결**: 기존 `catch (DataIntegrityViolationException)`이 모든 위반을 "아이디 중복"으로 바꾸고 있어서, 이메일 제약을 추가하면 이메일 위반이 틀린 메시지로 나가는 문제가 생겼다. 제약 이름을 `uk_members_email`로 고정하고 Hibernate의 `getConstraintName()`으로 구분했다. 메시지 문자열 검색은 아이디 값에 "email"이 있으면 틀려서 버렸다.

## 복습 질문

<details>
<summary>Q1. 서비스에서 existsByEmail로 검사하는 것만으로는 왜 부족하고, DB unique 제약은 왜 필요한가요?</summary>

**답**: 
(여기에 직접 답을 적어보세요)

<details>
<summary>해설</summary>

검사(SELECT)와 저장(INSERT)이 별개의 단계라서 그 사이에 다른 요청이 끼어들 수 있다(check-then-act race). 같은 이메일의 가입 요청 A, B가 동시에 오면 둘 다 `existsByEmail`이 false를 보고 통과하고 둘 다 INSERT한다. 트랜잭션 격리 수준 기본값(READ COMMITTED)에서는 A가 아직 커밋하지 않은 행을 B가 볼 수 없다. DB unique 제약은 INSERT 시점에 DB가 직렬화해서 하나만 성공시키므로 최종 방어선이다. 그래서 `AuthService.register`에서 `saveAndFlush`를 try/catch로 감싸 위반을 409로 바꾼다 (500 방지). 검사는 대부분의 경우 사용자에게 친절한 메시지를 주기 위한 용도다.
</details>
</details>

<details>
<summary>Q2. 이메일을 정규화(trim + 소문자)하지 않으면 어떤 문제가 생기나요?</summary>

**답**: 
(여기에 직접 답을 적어보세요)

<details>
<summary>해설</summary>

`Alice@x.com`, `alice@x.com`, ` alice@x.com`은 문자열로는 모두 다른 값이라 `existsByEmail`도 DB unique 제약도 서로 다른 이메일로 본다. 그래서 한 사람이 대소문자나 공백만 바꿔 여러 계정을 만들 수 있고, 검사·제약이 있는데도 우회된다. 정규화는 비교 이전에 값을 한 가지 형태로 통일해서 "같은 이메일"의 정의를 DB 제약과 맞추는 작업이다. 이번에는 `AuthService.normalizeEmail`에서 하고, 기존에 정규화되지 않은 행은 SQL로 직접 맞춰야 한다(task 파일의 2단계).
</details>
</details>

<details>
<summary>Q3. (기존 코드 연결) ContentService.saveContent의 findByExternalIdAndCategory 중복 체크는 같은 race condition에 노출돼 있나요? 코드 근거로 설명해 보세요.</summary>

**답**: 
(여기에 직접 답을 적어보세요)

<details>
<summary>해설</summary>

현재 코드에서 중복 체크는 `isAlreadySaved` → `existsByMemberIdAndExternalIdAndCategory`(task 16에서 `findByExternalIdAndCategory`를 교체)이고, 이것만 보면 check-then-act라서 똑같이 race에 노출된다 (`ContentService.saveContent`의 3번 체크와 `contentRepository.save` 사이에 다른 요청이 끼어들 수 있음). 다만 방어선이 이미 있다. `Content`에 `uk_content_member_external_category` unique 제약이 있고(`@Table`), `save()`를 try/catch로 감싸 `DataIntegrityViolationException`의 SQLState 23505만 `IllegalStateException("이미 저장된 콘텐츠입니다.")`(400)로 바꾼다 (decisions 2026-10-27). 그래서 "체크 + 제약 + 변환" 구조는 이번 이메일 가입과 같다. 남는 구멍: `external_id`가 null인 직접 입력 행은 PostgreSQL이 NULL을 서로 다른 값으로 취급해 제약에 걸리지 않는다(decisions에 기록된 한계). 또 이번 가입과 달리 유니크 위반의 종류가 하나뿐이라 제약 이름 구분 없이 SQLState로 충분했다.
</details>
</details>
