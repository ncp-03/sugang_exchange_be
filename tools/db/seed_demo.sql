-- =====================================================================
-- 시연용 데이터 준비 (수동 실행) — 2026-10-29 개정
--
-- 시연: 졸린고양이(2024000001, 발표자 PC) ↔ 바쁜다람쥐(2024000002, 팀원 PC)가
--       실제로 서로 강의를 맞교환한다 (고양이 웹프01 ↔ 다람쥐 프언01). 두 사람 모두 화면에서 직접 요청·확정한다.
-- 체험: 시연 당일 방문자가 직접 해 볼 수 있도록 체험 계정 5쌍(10개)을 만든다.
--       쌍마다 서로 다른 과목을 써서 다른 쌍·시연 계정과 절대 엇갈려 매칭되지 않는다.
--
-- 사용:  mysql> USE course_change;  SOURCE tools/db/seed_demo.sql;
--   또는 docker exec -i course-change-mysql mysql -ucourse_change -pcourse_change \
--            course_change < tools/db/seed_demo.sql
--
-- 주의: 시연 DB(course_change) 전용. course_change_loadtest 에는 절대 실행하지 말 것.
--       리허설·체험이 끝날 때마다 다시 실행하면 처음 상태로 돌아간다.
--       (교환으로 바뀐 수강내역도 V2 원래 상태로 되돌린다)
-- =====================================================================

-- 이 DB가 시연 DB인지 확인 (loadtest DB는 학생이 1만 명 이상)
SELECT DATABASE() AS current_db, COUNT(*) AS students FROM students;

-- 안전장치: 시연 DB(course_change)가 아니면 CHECK 위반 오류(3819)로 여기서 멈춘다.
-- (mysql 클라이언트는 파일 실행 중 오류가 나면 이후 문장을 실행하지 않는다)
DROP TEMPORARY TABLE IF EXISTS seed_demo_guard;
CREATE TEMPORARY TABLE seed_demo_guard (
    db_name VARCHAR(64),
    CONSTRAINT ck_seed_demo_only_course_change CHECK (db_name = 'course_change')
);
INSERT INTO seed_demo_guard VALUES (DATABASE());
DROP TEMPORARY TABLE seed_demo_guard;

-- ---------------------------------------------------------------- 1. 교환 데이터 초기화
-- 외래 키 때문에 자식 테이블부터 지운다. 강의 데이터는 건드리지 않는다.
DELETE FROM penalty_events;
DELETE FROM match_participants;
DELETE FROM match_sessions;
DELETE FROM exchange_preferences;
DELETE FROM exchange_requests;
DELETE FROM wishlists;
UPDATE students SET blocked_until = NULL;          -- 패널티 차단 해제

-- ---------------------------------------------------------------- 2. 체험 계정 (없으면 추가)
-- 학번 = 비밀번호. 쌍 번호 n의 A = 2026000n01, B = 2026000n02
INSERT INTO students (student_no, nickname, department, year_level, max_credits, password_hash) VALUES
    ('2026000101', '체험1A 푸른고래',   '체험', 3, 19, SHA2('2026000101:2026000101', 256)),
    ('2026000102', '체험1B 노란병아리', '체험', 3, 19, SHA2('2026000102:2026000102', 256)),
    ('2026000201', '체험2A 빨간여우',   '체험', 3, 19, SHA2('2026000201:2026000201', 256)),
    ('2026000202', '체험2B 하얀곰',     '체험', 3, 19, SHA2('2026000202:2026000202', 256)),
    ('2026000301', '체험3A 초록개구리', '체험', 3, 19, SHA2('2026000301:2026000301', 256)),
    ('2026000302', '체험3B 보라나비',   '체험', 3, 19, SHA2('2026000302:2026000302', 256)),
    ('2026000401', '체험4A 주황호랑이', '체험', 3, 19, SHA2('2026000401:2026000401', 256)),
    ('2026000402', '체험4B 까만고양이', '체험', 3, 19, SHA2('2026000402:2026000402', 256)),
    ('2026000501', '체험5A 분홍돌고래', '체험', 3, 19, SHA2('2026000501:2026000501', 256)),
    ('2026000502', '체험5B 회색코끼리', '체험', 3, 19, SHA2('2026000502:2026000502', 256))
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), blocked_until = NULL;

-- ---------------------------------------------------------------- 3. 수강내역 원래대로
-- 시연 학생 1~10: V2 시드 상태로 복원. 단, 졸린고양이(1)는 "수강신청 전"(0건)으로 시작한다.
-- (그래서 웹프로그래밍 01 = 반짝이는별·배고픈판다 2/35, 고양이가 신청하면 3/35)
DELETE FROM enrollments WHERE student_id BETWEEN 1 AND 10;
INSERT INTO enrollments (student_id, offering_id, course_id) VALUES
    (2, 9, 5), (2, 4, 2), (2, 1, 1), (2, 6, 3),
    (3, 2, 1), (3, 14, 8), (3, 16, 9), (3, 17, 10),
    (4, 11, 6), (4, 12, 7), (4, 8, 4), (4, 19, 12), (4, 21, 13),
    (5, 18, 11), (5, 20, 12), (5, 13, 8), (5, 6, 3),
    (6, 19, 12), (6, 3, 2), (6, 15, 9), (6, 2, 1),
    (7, 13, 8), (7, 16, 9), (7, 17, 10), (7, 21, 13),
    (8, 11, 6), (8, 4, 2), (8, 5, 3), (8, 19, 12), (8, 9, 5), (8, 14, 8),
    (9, 1, 1), (9, 7, 4), (9, 12, 7), (9, 20, 12), (9, 15, 9),
    (10, 10, 5), (10, 8, 4), (10, 3, 2), (10, 18, 11);
-- 프로그래밍언어론 01(offering 9, 정원 2) = 다람쥐 + 부엉이 → 마감 상태

-- 체험 계정: 쌍마다 A는 01분반, B는 02분반을 1과목만 수강 (교환 조건 항상 통과)
--   1쌍 자료구조(1↔2) · 2쌍 운영체제(3↔4) · 3쌍 컴퓨터네트워크(5↔6)
--   4쌍 데이터베이스(7↔8) · 5쌍 인공지능개론(19↔20)
--   ※ 프로그래밍언어론(시연)·대학영어·이산수학(배경 요청)은 일부러 쓰지 않는다.
DELETE e FROM enrollments e JOIN students s ON s.student_id = e.student_id
WHERE s.department = '체험';

INSERT INTO enrollments (student_id, offering_id, course_id)
SELECT s.student_id, t.offering_id, t.course_id
FROM students s JOIN (
    SELECT '2026000101' AS no, 1 AS offering_id, 1 AS course_id UNION ALL
    SELECT '2026000102', 2, 1 UNION ALL
    SELECT '2026000201', 3, 2 UNION ALL
    SELECT '2026000202', 4, 2 UNION ALL
    SELECT '2026000301', 5, 3 UNION ALL
    SELECT '2026000302', 6, 3 UNION ALL
    SELECT '2026000401', 7, 4 UNION ALL
    SELECT '2026000402', 8, 4 UNION ALL
    SELECT '2026000501', 19, 12 UNION ALL
    SELECT '2026000502', 20, 12
) t ON t.no = s.student_no;

-- ---------------------------------------------------------------- 4. 희망과목(찜)
-- 시나리오: 졸린고양이는 프로그래밍언어론 01을 꼭 듣고 싶었지만 정원 마감으로 못 잡는다.
--          (02분반은 월수 13:30이라 소프트웨어공학과 시간이 겹쳐 대안이 안 된다 → 희과담에 넣지 않음)
--          대신 웹프로그래밍 01을 잡고, 맞교환에서 '웹프01 내놓기 × 프언01 받기'로 다람쥐와 교환한다.
-- 수강신청 화면 '수강희망과목' 탭에는 순위 없이 이 순서대로 나온다.
INSERT INTO wishlists (student_id, offering_id, preference_rank) VALUES
    (1,  9, 1),   -- 프로그래밍언어론 01 (화목 15:00, 정원 2/2 마감 → 신청 실패. 교환으로 받을 강의)
    (1, 18, 2),   -- 웹프로그래밍 01   (화목 16:30, 신청 성공 → 교환으로 내놓을 강의)
    (1, 12, 3),   -- 소프트웨어공학 01 (월수 13:30)
    (1,  3, 4),   -- 운영체제 01       (월수 10:30)
    (1,  7, 5);   -- 데이터베이스 01   (월수 12:00)
-- 위 5과목은 서로, 그리고 프언01과도 시간이 겹치지 않는다 (전부 신청해도 15학점 + 교환 후 15학점).

-- ※ 찜 = 수강신청 '수강희망과목'(희과담). 같은 테이블이라 수강신청에서 담은 과목이 맞교환 찜에 그대로 나온다.

-- 바쁜다람쥐: 웹프로그래밍 01이 1순위 → 맞교환 내 페이지에서 '프언01 내놓기 × 웹프01 받기'를 바로 고를 수 있다.
--   웹프01(화목 16:30)은 다람쥐의 남은 수강(운영체제02 화목 13:30, 자료구조01 월수 09:00, 컴네02 월수 15:00)과 겹치지 않는다.
--   2·3순위는 교환 요청을 낸 사람이 없는 과목이라 엇갈린 매칭이 생기지 않는다.
INSERT INTO wishlists (student_id, offering_id, preference_rank) VALUES
    (2, 18, 1),   -- 웹프로그래밍 01
    (2, 17, 2),   -- 글쓰기와 소통 01
    (2, 11, 3);   -- 컴파일러 01

-- 체험 계정: 1순위 = 상대 분반 (A는 02, B는 01), 2·3순위 = 희과담 예시 (웹프로그래밍 01, 글쓰기와 소통 01)
--   2·3순위는 매칭되지 않는다. 시연 중 고양이가 웹프01을 내놓지만 고양이는 프언01만 원하므로
--   체험 계정(프언01 미보유)과는 서로 일치하지 않는다.
INSERT INTO wishlists (student_id, offering_id, preference_rank)
SELECT s.student_id, x.offering_id, x.preference_rank
FROM students s CROSS JOIN (SELECT 18 AS offering_id, 2 AS preference_rank UNION ALL SELECT 17, 3) x
WHERE s.department = '체험';

INSERT INTO wishlists (student_id, offering_id, preference_rank)
SELECT s.student_id, t.offering_id, 1
FROM students s JOIN (
    SELECT '2026000101' AS no, 2 AS offering_id UNION ALL
    SELECT '2026000102', 1 UNION ALL
    SELECT '2026000201', 4 UNION ALL
    SELECT '2026000202', 3 UNION ALL
    SELECT '2026000301', 6 UNION ALL
    SELECT '2026000302', 5 UNION ALL
    SELECT '2026000401', 8 UNION ALL
    SELECT '2026000402', 7 UNION ALL
    SELECT '2026000501', 20 UNION ALL
    SELECT '2026000502', 19
) t ON t.no = s.student_no;

-- ---------------------------------------------------------------- 5. 배경 교환 요청 (시장이 비어 보이지 않게)
-- 강의 검색 화면의 '희망자 / 보유자' 숫자와 대기열 순위가 이 데이터에서 나온다.
-- 시연·체험 계정이 쓰는 과목과 겹치지 않으므로 누구와도 매칭되지 않는다.

-- 용감한펭귄(3) : 대학영어 02 내놓고 01 희망
INSERT INTO exchange_requests (student_id, offered_enrollment_id, status, queued_at)
SELECT e.student_id, e.enrollment_id, 'WAITING', NOW() - INTERVAL 20 MINUTE
FROM enrollments e WHERE e.student_id = 3 AND e.offering_id = 16;
INSERT INTO exchange_preferences (request_id, target_offering_id, preference_rank)
SELECT r.request_id, 15, 1
FROM exchange_requests r JOIN enrollments e ON e.enrollment_id = r.offered_enrollment_id
WHERE r.student_id = 3 AND e.offering_id = 16;

-- 씩씩한토끼(7) : 대학영어 02 내놓고 01 희망 (같은 대기열 경쟁자)
INSERT INTO exchange_requests (student_id, offered_enrollment_id, status, queued_at)
SELECT e.student_id, e.enrollment_id, 'WAITING', NOW() - INTERVAL 10 MINUTE
FROM enrollments e WHERE e.student_id = 7 AND e.offering_id = 16;
INSERT INTO exchange_preferences (request_id, target_offering_id, preference_rank)
SELECT r.request_id, 15, 1
FROM exchange_requests r JOIN enrollments e ON e.enrollment_id = r.offered_enrollment_id
WHERE r.student_id = 7 AND e.offering_id = 16;

-- 조용한부엉이(8) : 이산수학 02 내놓고 01 희망
INSERT INTO exchange_requests (student_id, offered_enrollment_id, status, queued_at)
SELECT e.student_id, e.enrollment_id, 'WAITING', NOW() - INTERVAL 5 MINUTE
FROM enrollments e WHERE e.student_id = 8 AND e.offering_id = 14;
INSERT INTO exchange_preferences (request_id, target_offering_id, preference_rank)
SELECT r.request_id, 13, 1
FROM exchange_requests r JOIN enrollments e ON e.enrollment_id = r.offered_enrollment_id
WHERE r.student_id = 8 AND e.offering_id = 14;

-- ---------------------------------------------------------------- 6. 확인
SELECT '졸린고양이 수강내역 (0)' AS check_name, COUNT(*) AS value
FROM enrollments WHERE student_id = 1
UNION ALL SELECT '졸린고양이 희망과목 (5)', COUNT(*) FROM wishlists WHERE student_id = 1
UNION ALL SELECT '다람쥐가 프언01 보유 (1)', COUNT(*) FROM enrollments WHERE student_id = 2 AND offering_id = 9
UNION ALL SELECT '졸린고양이 1순위 = 프언01 (1)', COUNT(*) FROM wishlists WHERE student_id = 1 AND offering_id = 9 AND preference_rank = 1
UNION ALL SELECT '다람쥐 1순위 = 웹프01 (1)', COUNT(*) FROM wishlists WHERE student_id = 2 AND offering_id = 18 AND preference_rank = 1
UNION ALL SELECT '다람쥐 희망과목 (3)', COUNT(*) FROM wishlists WHERE student_id = 2
UNION ALL SELECT '체험 계정 희망과목 (30)', COUNT(*) FROM wishlists w JOIN students s ON s.student_id = w.student_id
          WHERE s.department = '체험'
UNION ALL SELECT '체험 계정 (10)', COUNT(*) FROM students WHERE department = '체험'
UNION ALL SELECT '체험 계정 수강 (10)', COUNT(*) FROM enrollments e JOIN students s ON s.student_id = e.student_id
          WHERE s.department = '체험'
UNION ALL SELECT '대기 중 교환 요청 (3)', COUNT(*) FROM exchange_requests WHERE status = 'WAITING'
UNION ALL SELECT '진행 중 세션 (0)', COUNT(*) FROM match_sessions WHERE status = 'PENDING';

-- 프로그래밍언어론 01분반이 정원 마감인지 (정원 2 / 신청 2), 웹프로그래밍 01은 자리가 남는지
SELECT o.offering_id, c.course_name, o.section_no, o.capacity,
       COUNT(e.enrollment_id) AS enrolled
FROM course_offerings o
JOIN courses c ON c.course_id = o.course_id
LEFT JOIN enrollments e ON e.offering_id = o.offering_id
WHERE o.offering_id IN (9, 10, 18)
GROUP BY o.offering_id, c.course_name, o.section_no, o.capacity;
