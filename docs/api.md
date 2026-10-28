# API 목록 (프론트 연동용)

기준: 소스 코드(컨트롤러·DTO). 실제 응답은 `/swagger-ui.html`에서 바로 확인할 수 있습니다.

## 공통

- 모든 경로는 `/api` 로 시작합니다. 로그인 뒤 요청에는 `Authorization: Bearer <accessToken>` 헤더가 필요합니다. (강의 조회만 로그인 없이 가능)
- 시각은 `Asia/Seoul` ISO 문자열(`2026-10-29T15:40:00`), 요일은 1(월)~5(금).
- **분반 ID(`offeringId`)** 가 강의·찜·교환 희망의 기준입니다. **수강 내역 ID(`enrollmentId`)** 는 "내가 가진 좌석"(내놓을 강의, 수강삭제)을 가리킬 때 씁니다.
- 에러 형식: `{ "status": 409, "code": "SCHEDULE_CONFLICT", "message": "교환하면 시간표가 겹칩니다." }`

## 인증·내 정보

| 메서드·경로 | 설명 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/auth/login` | 로그인 | `{ studentNo, password }` (초기 비번 = 학번) | `{ accessToken, tokenType, expiresInSeconds, studentId, nickname }` |
| `GET /api/students/me` | 내 정보 | - | `{ studentId, nickname, department, yearLevel, maxCredits, currentCredits, blockedUntil }` |

## 수강 (수강신청 화면)

| 메서드·경로 | 설명 |
|---|---|
| `GET /api/enrollments/me` | 내 수강 목록. `{ totalCredits, maxCredits, count, enrollments: [{ enrollmentId, enrolledAt, course }] }` |
| `POST /api/enrollments` | **수강신청**. `{ offeringId }` → 201 `{ enrollmentId, enrolledAt, course }` |
| `DELETE /api/enrollments/{enrollmentId}` | **수강삭제** → 204 |

수강신청이 거절되는 경우: `ALREADY_ENROLLED`(같은 분반 중복), `SAME_COURSE_ALREADY_HELD`(같은 과목 다른 분반), `COURSE_FULL`(정원), `CREDIT_LIMIT_EXCEEDED`(학점 상한), `SCHEDULE_CONFLICT`(시간표 겹침). 모두 409입니다.
수강삭제는 교환 요청이 걸린 좌석이면 409 `ENROLLMENT_IN_EXCHANGE`.

## 강의

| 메서드·경로 | 설명 |
|---|---|
| `GET /api/courses?keyword&department&courseType&professor&targetYear&weekday&page&size` | 검색. 페이지 응답 `{content, page, size, totalElements, totalPages, hasNext}` |
| `GET /api/courses/filters` | 드롭다운 값 `{ departments[], professors[], courseTypes[{value,label}], targetYears[{value,label}], weekdays[{value,label}] }` |
| `GET /api/courses/{offeringId}` | 분반 상세 |

- `keyword`: 과목명·학수번호·교수명 부분 일치
- `professor`: 교수명 정확히 일치 (`filters`의 `professors` 값)
- `targetYear`: 대상학년 1~6 (`filters`의 `targetYears` 값)

`course` 객체: `offeringId, courseId, courseCode, courseName, sectionNo, professorName, department, targetYear, courseType, courseTypeLabel, credits, capacity, enrolledCount, full, classroom, schedules[{weekday, weekdayLabel, startTime, endTime}], scheduleText, exchange{ wantCount, offerCount }`

## 찜

| 메서드·경로 | 설명 |
|---|---|
| `GET /api/wishlists/me` | `{ count, wishlists: [{ wishlistId, rank, createdAt, alreadyEnrolled, course }] }` — **희망 순위(rank) 순** |
| `POST /api/wishlists` | `{ offeringId }` 찜 추가. 새 찜은 순위 맨 뒤로 들어갑니다 |
| `PATCH /api/wishlists/order` | **희망 순위 변경**. `{ offeringIds: [1순위, 2순위, ...] }` — 지금 찜한 분반 전체를 순서대로 보냅니다 |
| `DELETE /api/wishlists/{offeringId}` | 찜 해제. 남은 찜의 순위를 1부터 다시 매깁니다 |

`PATCH`는 보낸 목록이 서버의 찜 목록과 다르면 400 `INVALID_PARAMETER`(그 사이 찜이 바뀐 경우). 다시 조회한 뒤 보내면 됩니다.

## 교환 요청

| 메서드·경로 | 설명 |
|---|---|
| `GET /api/exchange-requests/eligibility?wantedOfferingIds=9,11` | **교환 쌍 가능 여부 표**. 생략하면 내 찜 목록 기준 |
| `POST /api/exchange-requests` | `{ offeredEnrollmentId, wantedOfferingIds: [1순위, 2순위...], autoMatch: true }` → 201 |
| `GET /api/exchange-requests/me` | `{ count, requests: [ExchangeRequest] }` |
| `GET /api/exchange-requests/{id}` | 1건 |
| `PATCH /api/exchange-requests/{id}` | `{ wantedOfferingIds }` 희망 분반 전체 교체 (WAITING일 때만) |
| `DELETE /api/exchange-requests/{id}` | 취소 (204) |
| `GET /api/exchange-requests/{id}/queue` | `{ requestId, status, position, competitors, score }` |
| `POST /api/exchange-requests/{id}/quick-match` | `{ matched, reason, candidatesChecked, session }` (못 찾아도 200) |

`eligibility` 응답:
```json
{
  "offeredCourses": [ /* 내 수강 내역의 분반 */ ],
  "wantedCourses":  [ /* 받을 후보 분반 */ ],
  "cells": [
    { "offeredEnrollmentId": 3, "offeredOfferingId": 10, "wantedOfferingId": 9,
      "allowed": false, "reasonCode": "SCHEDULE_CONFLICT", "reason": "시간표 겹침",
      "creditsAfterSwap": 16 }
  ]
}
```
화면의 교환 쌍 표는 `offeredEnrollmentId + '-' + wantedOfferingId` 로 칸을 찾아 `allowed`/`reason`을 그리면 됩니다.
판단 기준은 교환 요청 등록·매칭·확정 때 쓰는 검사와 같은 코드입니다.

`ExchangeRequest`: `{ requestId, status(WAITING|MATCHED|COMPLETED|CANCELLED), queuedAt, offeredEnrollmentId, offeredCourse, preferences:[{rank, course}], matchSessionId }`

## 매칭 세션

| 메서드·경로 | 설명 |
|---|---|
| `GET /api/match-sessions/me` | 진행 중 세션 목록 (보통 0~1개) |
| `GET /api/match-sessions/me/history` | **교환 기록**. `{ count, items: [{ sessionId, completedAt, partnerNickname, giveCourse, receiveCourse }] }` |
| `GET /api/match-sessions/{id}` | 상세 |
| `POST /api/match-sessions/{id}/accept` | 동의. 둘 다 동의하면 즉시 교환 실행 → `status: COMPLETED` |
| `POST /api/match-sessions/{id}/reject` | 거절 (감점 2점) |
| `POST /api/match-sessions/{id}/wake` | 상대 깨우기 (최대 3회) |

`Session`: `{ sessionId, status(PENDING|COMPLETED|REJECTED|EXPIRED|INVALIDATED), openedAt, expiresAt, remainingSeconds, firstAcceptedAt, participants:[{ me, nickname, responseStatus, giveCourse, receiveCourse, wakeSentCount }] }`

화면의 남은 시간은 `remainingSeconds`(서버 기준)로 시작해 1초씩 줄이고, 몇 초마다 세션을 다시 조회해 맞춥니다.

## 에러 코드

| 코드 | HTTP | 뜻 |
|---|---|---|
| `MISSING_TOKEN` / `INVALID_TOKEN` | 401 | 로그인 필요 / 토큰 오류·서버 재시작 |
| `INVALID_CREDENTIALS` | 401 | 학번·비밀번호 오류 |
| `INVALID_PARAMETER` | 400 | 요청 값 오류 (찜 순서 불일치 등) |
| `COURSE_NOT_FOUND` / `ENROLLMENT_NOT_FOUND` / `REQUEST_NOT_FOUND` / `SESSION_NOT_FOUND` | 404 | 없는 대상 |
| `NOT_MY_ENROLLMENT` / `NOT_MY_REQUEST` / `NOT_MY_SESSION` | 403 | 내 것이 아님 |
| `STUDENT_BLOCKED` | 403 | 패널티로 이용 제한 |
| `ALREADY_ENROLLED` / `SAME_COURSE_ALREADY_HELD` / `COURSE_FULL` | 409 | 수강신청 불가 |
| `CREDIT_LIMIT_EXCEEDED` / `SCHEDULE_CONFLICT` | 409 | 학점·시간표 조건 불가 |
| `ENROLLMENT_IN_EXCHANGE` | 409 | 교환 중인 좌석은 삭제 불가 |
| `REQUEST_ALREADY_EXISTS` | 409 | 같은 좌석으로 진행 중인 요청 있음 |
| `ALREADY_RESPONDED` / `SESSION_EXPIRED` / `SESSION_NOT_PENDING` | 409 | 세션 상태 오류 |
| `WAKE_LIMIT_EXCEEDED` | 409 | 깨우기 3회 초과 |
| `EXCHANGE_CONFLICT` | 409 | 동시에 다른 요청이 먼저 처리됨 (재시도) |
