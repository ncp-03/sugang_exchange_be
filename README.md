# 수강신청 교환 마켓 · 백엔드 (course_change_be)

돈 거래 없이 수강 중인 강의를 서로 맞교환하는 서비스의 백엔드입니다. 가상 학사 시스템(학생·강의·수강) 위에 교환 기능이 붙는 구조입니다. 모의 서비스이며 실제 수강신청과 무관합니다.

| 항목 | 내용 |
|---|---|
| 언어·프레임워크 | Java 17, Spring Boot 3.5 (Web, Data JPA, Validation, Actuator) |
| DB | MySQL 8.0 (스키마는 Flyway가 자동 생성) |
| 인증 | 학번 + 비밀번호 로그인 → HMAC 서명 토큰 (Bearer) |
| API 문서 | springdoc Swagger UI |
| 테스트 | JUnit 5 + Testcontainers(실제 MySQL 컨테이너) |
| CI | GitHub Actions (테스트 + jar 실행 스모크 테스트) |

## 실행 방법

### 준비물
- JDK 17 이상
- Docker (로컬 MySQL과 테스트용 컨테이너에 사용)

### 1. DB 켜기
```bash
docker compose up -d        # MySQL 8.0 (localhost:3306, DB/계정/비번 모두 course_change)
docker compose down         # 중지
docker compose down -v      # 데이터까지 지우고 처음부터
```

### 2. 서버 켜기
```bash
./gradlew bootRun
```
서버가 뜨면 Flyway가 V1(테이블 11개), V2(가상 학생 10명·강의 21개 등), V3(로그인용 비밀번호 해시)를 자동 적용합니다.

- Swagger UI: http://localhost:8080/swagger-ui.html
- 헬스체크: http://localhost:8080/actuator/health

### 3. 로그인 (기본 모드)
```bash
curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"studentNo":"2024000001","password":"2024000001"}'
```
응답의 `accessToken`을 `Authorization: Bearer <토큰>` 헤더로 보냅니다. 토큰 유효 시간은 8시간입니다.
가상 데이터라서 **초기 비밀번호는 학번과 같습니다.** Swagger UI는 우측 상단 **Authorize**에 토큰을 넣으면 됩니다.

### 4. 테스트
```bash
./gradlew test                          # Docker가 켜져 있어야 함 (Testcontainers)
bash scripts/smoke-test.sh              # 서버를 띄운 뒤 주요 API를 curl로 확인
```

### 환경변수 (배포 시)

| 이름 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/course_change?...` | DB 주소 (NCP Cloud DB for MySQL 주소로 교체) |
| `DB_USERNAME` / `DB_PASSWORD` | `course_change` | DB 계정 |
| `CORS_ALLOWED_ORIGINS` | localhost:3000, 5173 | 프론트 주소 (쉼표로 여러 개) |
| `PORT` | `8080` | 서버 포트 |
| `APP_AUTH_MODE` | `TOKEN` | `TOKEN`: 로그인 토큰만 인정. `DEMO`: `X-Student-Id` 헤더도 인정 (로컬·부하테스트 전용, **공개 서버 금지**) |
| `APP_AUTH_SECRET` | 비어 있음 | 토큰 서명 키. 비우면 기동마다 무작위 생성(재시작 시 토큰 무효). 운영은 고정 권장 |
| `APP_MATCHING_LOCK_MODE` | `NONE` | 교환 락 전략. `NONE`(락 없음) / `PESSIMISTIC`(비관적 락). 부하테스트 비교용 |
| `APP_MATCHING_ACCEPT_TIMEOUT` | `120` | 매칭 후 양쪽이 동의해야 하는 제한 시간(초) |
| `APP_MATCHING_EXPIRE_SCHEDULER` | `true` | 만료 세션 자동 처리 스케줄러 (5초마다) |

배포용 jar: `./gradlew bootJar` → `build/libs/app.jar`

락 모드는 런타임에 못 바꾸고 **재시작이 필요합니다.**
```bash
java -jar app.jar --app.matching.lock-mode=PESSIMISTIC
```

## 문서
- [API 목록](docs/api.md): 엔드포인트 표, 에러 코드, 응답 필드
- [ERD](docs/erd.png) (원본 [erd.mmd](docs/erd.mmd)): 테이블 11개 관계도
- [부하테스트 안내](loadtest/실행_안내.md): 락 없음/있음 비교 실행 방법
- 스키마 원본: `src/main/resources/db/migration/V1__init_schema.sql`

## API 요약

| 구분 | 메서드·경로 | 설명 |
|---|---|---|
| 인증 | `POST /api/auth/login` | 학번·비밀번호 로그인, 토큰 발급 |
| 강의 | `GET /api/courses`, `/api/courses/filters`, `/api/courses/{offeringId}` | 검색·필터·상세 (요일은 1~5, 월~금) |
| 학생 | `GET /api/students`, `/api/students/me` | 학생 목록, 내 정보 |
| 수강 | `GET /api/enrollments/me` | 내 수강 목록 |
| 찜 | `GET /api/wishlists/me`, `POST /api/wishlists`, `DELETE /api/wishlists/{offeringId}` | 하트(분반 단위) |
| 교환 요청 | `POST /api/exchange-requests`, `GET /api/exchange-requests/me`, `GET·PATCH·DELETE /api/exchange-requests/{id}` | 등록·조회·희망 수정·취소 |
| 대기열·매칭 | `GET /api/exchange-requests/{id}/queue`, `POST /api/exchange-requests/{id}/quick-match` | 대기열 순위, 빠른 매칭 |
| 매칭 세션 | `GET /api/match-sessions/me`, `GET /api/match-sessions/{id}`, `POST .../accept`, `.../reject`, `.../wake` | 동의·거절·깨우기(최대 3회) |
| 인프라 | `GET /actuator/health` | 헬스체크 |

## 구현 현황

| 기능 | 상태 |
|---|---|
| 서버 뼈대, 공통 에러 형식, Swagger | 완료 |
| 강의 목록·검색·상세·필터 | 완료 |
| 학번·비밀번호 로그인, 토큰 인증 | 완료 |
| 내 정보, 내 수강 목록 | 완료 |
| 찜(하트) | 완료 |
| 교환 요청 등록·수정·취소 (희망 분반 우선순위) | 완료 |
| 빠른 매칭 (서로 원하는 1:1, 학점·시간표·같은 과목 검사) | 완료 |
| 매칭 세션(동의·거절·깨우기·만료), 한 트랜잭션 좌석 교환 | 완료 |
| 대기열 우선순위 점수·순위 | 완료 |
| 패널티(거절·잠수 감점, 누적 시 차단) | 완료 |
| 락 전략 전환 (NONE / PESSIMISTIC) | 완료 |
| 알림 (NCP Simple & Easy Notification Service) | 예정 |
| 3명 이상 순환 교환 (최적화 매칭) | 확장 계획 |
| 매크로·과도한 요청 제한 | 앱 밖(서버·네트워크)에서 처리 |

## 핵심 규칙
- **빠른 매칭**: 내가 원하는 분반을 가진 사람이 내가 가진 분반을 원할 때만 짝지음. 후보는 내 희망 순위 → 우선순위 점수 → 먼저 등록한 순으로 정렬
- **우선순위 점수** = 학년×10 + 기다린 분(최대 120) + 학점 여유 − 감점×5 (가중치는 `application.yml`의 `score-*`)
- **교환 확정**: 양쪽이 제한 시간(기본 120초) 안에 동의하면 두 수강 내역을 한 트랜잭션으로 맞바꿈. 한쪽이라도 실패하면 전체 롤백
- **패널티**: 거절 2점, 잠수(만료까지 무응답) 5점. 누적 10점 이상이면 60분간 매칭 차단 (`penalty-*`)
- **같은 분반끼리는 교환 불가**, 같은 과목의 다른 분반이나 다른 과목은 교환 가능. 원하는 분반은 분반까지 정확히 지정
- 학번과 로그인 정보는 응답에 노출하지 않고 익명 닉네임만 보여줌

## 폴더 구조
```
src/main/java/com/ncp03/coursechange/
├─ common/      에러 처리, CORS·Swagger 설정, 토큰 인증, 매칭 설정
├─ domain/      JPA 엔티티·리포지토리
├─ auth/        로그인 API
├─ course/      강의 조회 API
├─ student/     학생 API
├─ enrollment/  내 수강 목록 API
├─ wishlist/    찜 API
└─ exchange/    교환 요청, 매칭, 패널티, 우선순위, 만료 스케줄러
src/main/resources/db/migration/   Flyway 스키마·샘플 데이터
loadtest/                          k6 시나리오, 초기화·검증 SQL
```

DB를 바꿀 때는 이미 적용된 `V1`~`V3` 파일을 고치지 말고 `V4__설명.sql`처럼 새 파일을 추가합니다. (적용된 파일을 수정하면 Flyway 체크섬 오류가 납니다.)
