-- =====================================================================
-- 시연용 데이터 준비 (수동 실행) — 2026-10-29 개정
--
-- 시연: 졸린고양이(2024000001, 발표자 PC) ↔ 바쁜다람쥐(2024000002, 팀원 PC)가
--       실제로 서로 강의를 맞교환한다 (고양이 웹프01 ↔ 다람쥐 프언01). 두 사람 모두 화면에서 직접 요청·확정한다.
--       고양이는 시연 중 7과목을 신청해 19/19학점(상한)을 꽉 채운다. 그래서 '일반 신청으로는 더 못 늘리지만
--       교환은 학점 합계가 그대로라 통과한다'를 보여줄 수 있다. 소프트웨어공학 01은 교환에 올리지 않고 그대로 유지한다.
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

-- 안전장치: 시연 DB(course_change)가 아니면 'Invalid JSON text' 오류(3141)로 여기서 멈춘다.
-- 추가 권한(임시 테이블 등)이 필요 없는 방식. mysql 클라이언트는 오류가 나면 이후 문장을 실행하지 않는다.
SELECT JSON_EXTRACT(IF(DATABASE() = 'course_change', '"OK: 시연 DB"', 'STOP: 시연 DB가 아님'), '$') AS db_guard;

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
    (2, 9, 5),   -- 바쁜다람쥐: 프언01 하나만 보유한다 (운영체제02·자료구조01·컴네02 제거)
                 --   이 한 건은 지울 수 없다: 교환에 내놓을 좌석이고, 프언01 정원 2/2(마감)를 만드는 데 필요하다
    (3, 2, 1), (3, 14, 8), (3, 16, 9), (3, 17, 10),
    (4, 11, 6), (4, 12, 7), (4, 8, 4), (4, 19, 12), (4, 21, 13),
    (5, 18, 11), (5, 20, 12), (5, 13, 8), (5, 6, 3),
    (6, 19, 12), (6, 3, 2), (6, 15, 9), (6, 2, 1),
    (7, 13, 8), (7, 16, 9), (7, 17, 10), (7, 21, 13),
    (8, 11, 6), (8, 4, 2), (8, 5, 3), (8, 19, 12), (8, 9, 5), (8, 14, 8),
    (9, 1, 1), (9, 7, 4), (9, 12, 7), (9, 20, 12), (9, 15, 9),
    (10, 10, 5), (10, 8, 4), (10, 3, 2), (10, 18, 11);
-- 프로그래밍언어론 01(offering 9, 정원 2) = 다람쥐 + 부엉이 → 마감 상태

-- 체험 계정: 수강내역을 **비워 둔다**. 미리 신청해 두지 않는다.
--   체험자가 직접 ① 수강희망과목 1순위(자기 분반)를 신청하고 ② 맞교환으로 상대 분반과 바꾼다.
--   그래서 체험자가 수강신청과 교환을 둘 다 경험한다.
--   쌍: 1쌍 자료구조(1↔2) · 2쌍 운영체제(3↔4) · 3쌍 컴퓨터네트워크(5↔6)
--       4쌍 데이터베이스(7↔8) · 5쌍 인공지능개론(19↔20)
--   ※ 프로그래밍언어론(시연)·대학영어·이산수학(배경 요청)은 일부러 쓰지 않는다.
--   ⚠ 쌍의 두 사람이 모두 ①을 해야 교환이 성립한다. 안내 슬라이드에 '먼저 수강신청'을 꼭 적을 것.
DELETE e FROM enrollments e JOIN students s ON s.student_id = e.student_id
WHERE s.department = '체험';

-- ---------------------------------------------------------------- 4. 희망과목(찜)
-- 시나리오: 졸린고양이는 프로그래밍언어론 01을 꼭 듣고 싶었지만 정원 마감으로 못 잡는다.
--          (02분반은 월수 13:30이라 소프트웨어공학과 시간이 겹쳐 대안이 안 된다 → 희과담에 넣지 않음)
--          대신 웹프로그래밍 01을 잡고, 맞교환에서 '웹프01 내놓기 × 프언01 받기'로 다람쥐와 교환한다.
-- 수강신청 화면 '수강희망과목' 탭에는 순위 없이 이 순서대로 나온다.
INSERT INTO wishlists (student_id, offering_id, preference_rank) VALUES
    (1,  9, 1),   -- 프로그래밍언어론 01 (화목 15:00, 3학점) 정원 2/2 마감 → 신청 실패. 교환으로 받을 강의
    (1, 12, 2),   -- 소프트웨어공학 01   (월수 13:30, 3학점) ★교환에 올리지 않고 끝까지 유지하는 강의
    (1, 10, 3),   -- 프로그래밍언어론 02 (월수 13:30, 3학점) 소공01과 겹쳐 신청 실패 → '시간표 겹침' 시연용
    (1,  1, 4),   -- 자료구조 01         (월수 09:00, 3학점)
    (1, 11, 5),   -- 컴파일러 01         (월수 12:00, 3학점)
    (1, 13, 6),   -- 이산수학 01         (화목 09:00, 3학점)
    (1, 16, 7),   -- 대학영어 02         (월수 10:30, 3학점)
    (1, 21, 8);   -- 체육과 건강 01      (금 13:30, 1학점) ← 1학점이라 19학점을 정확히 맞출 수 있다
-- ※ 웹프로그래밍 01(offering 18)은 일부러 희과담에 넣지 않는다.
--   ① 다람쥐의 1순위가 웹프01이므로, 두 사람의 수강희망과목 목록이 한 건도 겹치지 않게 한다
--      (같은 분반이 양쪽 목록에 있으면 한쪽이 신청에 성공했을 때 화면이 헷갈린다)
--   ② '희과담에 대안이 없어서 개설강좌 목록 탭에서 웹프01을 찾아 신청한다'는 흐름이 더 자연스럽다
--
-- 신청 순서 = 희과담 위에서 아래로. 순서가 중요하다.
--   1순위 프언01  → 정원 2/2 마감            → 409 '정원이 모두 찼습니다'
--   2순위 소공01  → 성공                     → 이걸 먼저 잡아야 다음 줄이 겹친다
--   3순위 프언02  → 소공01과 월수 13:30 겹침 → 409 '시간표가 겹칩니다'
--   4~8순위       → 전부 성공 (자료구조01·컴파일러01·이산수학01·대학영어02·체육01 = 16학점)
--   그 다음 개설강좌 목록 탭에서 웹프01 신청  → 19/19 로 꽉 찬다
--   이후 아무 과목(예: 컴퓨터네트워크 01) 신청 → 409 '학점 상한 초과'
--
-- 신청할 7과목 = 1·11·12·13·16·18·21 = 3학점×6 + 1학점 = 19학점 = 고양이 max_credits(19).
-- 서로, 그리고 프언01(화목 15:00)과도 수업 시간이 겹치지 않는다.
-- 교환은 웹프01(3) 빠지고 프언01(3) 들어와 19/19 그대로이므로 재검사를 통과한다
--   (ExchangeValidator: after > max_credits 일 때만 막으므로 19 > 19 는 false).
--
-- ※ 맞교환 '찜' 목록에는 이미 수강 중인 분반이 빠지므로, 7과목을 다 신청하면 찜에 프언01·프언02만 남는다.
--   그래서 내 페이지의 '교환 가능 여부' 표가 7(내놓기) × 2(받기)로 만들어지고 아래가 모두 보인다.
--     · 웹프01 내놓고 프언01 받기        → 가능            (시연에서 실제로 쓰는 쌍)
--     · 체육01(1학점) 내놓고 프언01 받기 → 학점 상한 초과   (19 - 1 + 3 = 21 > 19)
--     · 웹프01 내놓고 프언02 받기        → 시간표 겹침      (소공01 월수 13:30이 그대로 남으므로)
--   → '가능'만 나오지 않고 불가 사유도 함께 보여 줄 수 있다.

-- ※ 찜 = 수강신청 '수강희망과목'(희과담). 같은 테이블이라 수강신청에서 담은 과목이 맞교환 찜에 그대로 나온다.

-- 바쁜다람쥐: 희망과목을 웹프01 **한 건만** 둔다.
--   시연에서 다람쥐의 역할은 10초다. 받기 후보가 하나뿐이면 잘못 고를 여지가 없다
--   (이전에 리허설 중 하트를 눌러 순위가 틀어져 다른 과목이 맨 위에 온 적이 있었다).
--   다람쥐 보유는 프언01 하나뿐이므로 '프언01 내놓기 × 웹프01 받기'가 유일한 조합이 된다.
--   웹프01(화목 16:30)은 겹칠 과목이 없다. 교환 후 3학점 ≤ 19.
--   고양이 희과담(9·12·10·1·11·13·16·21)과 겹치지 않는다 (화면 '신청완료' 표시 혼동 방지).
INSERT INTO wishlists (student_id, offering_id, preference_rank) VALUES
    (2, 18, 1);   -- 웹프로그래밍 01   ← 교환으로 받을 강의

-- 체험 계정 희망과목 3건: 1순위 = 자기 분반(먼저 신청할 것) / 2순위 = 짝의 분반(교환으로 받을 것) / 3순위 = 예시
--   1순위를 신청하면 그 분반은 찜에서 빠지므로, 맞교환 '받기' 목록 맨 위가 곧 짝의 분반이 된다.
--   3순위 글쓰기와 소통 01(17)은 ① 체험 계정 누구도 보유하지 않고 ② 고양이도 보유하지 않으며
--     ③ 아무도 교환에 내놓지 않는다 → 엇갈린 매칭도, '신청완료' 표시 혼동도 생기지 않는다.
--   체험 계정이 내놓는 분반은 전부 1~8·19·20이고, 다른 체험 계정의 희망 목록에는 '자기 짝의 분반'으로만
--     들어 있으므로 쌍이 엇갈릴 수 없다.
--   배경 요청(펭귄·토끼 16→15, 부엉이 14→13)과도 엇갈리지 않는다: 체험 계정은 16도 14도 내놓지 않는다.
--   고양이는 웹프01을 내놓지만 프언01·프언02만 원하고, 체험 계정은 그 둘을 갖고 있지 않다.
INSERT INTO wishlists (student_id, offering_id, preference_rank)
SELECT s.student_id, v.offering_id, v.preference_rank
FROM students s JOIN (
    -- 학번            1순위=자기 분반   2순위=짝의 분반
    SELECT '2026000101' AS no,  1 AS offering_id, 1 AS preference_rank UNION ALL
    SELECT '2026000101',        2,                2 UNION ALL
    SELECT '2026000102',        2,                1 UNION ALL
    SELECT '2026000102',        1,                2 UNION ALL
    SELECT '2026000201',        3,                1 UNION ALL
    SELECT '2026000201',        4,                2 UNION ALL
    SELECT '2026000202',        4,                1 UNION ALL
    SELECT '2026000202',        3,                2 UNION ALL
    SELECT '2026000301',        5,                1 UNION ALL
    SELECT '2026000301',        6,                2 UNION ALL
    SELECT '2026000302',        6,                1 UNION ALL
    SELECT '2026000302',        5,                2 UNION ALL
    SELECT '2026000401',        7,                1 UNION ALL
    SELECT '2026000401',        8,                2 UNION ALL
    SELECT '2026000402',        8,                1 UNION ALL
    SELECT '2026000402',        7,                2 UNION ALL
    SELECT '2026000501',       19,                1 UNION ALL
    SELECT '2026000501',       20,                2 UNION ALL
    SELECT '2026000502',       20,                1 UNION ALL
    SELECT '2026000502',       19,                2
) v ON v.no = s.student_no;

-- 3순위: 전원 공통 (글쓰기와 소통 01)
INSERT INTO wishlists (student_id, offering_id, preference_rank)
SELECT s.student_id, 17, 3 FROM students s WHERE s.department = '체험';

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
UNION ALL SELECT '졸린고양이 희망과목 (8)', COUNT(*) FROM wishlists WHERE student_id = 1
UNION ALL SELECT '졸린고양이 최대학점 (19)', max_credits FROM students WHERE student_id = 1
UNION ALL SELECT '고양이 신청 대상 7과목 학점합 (19)',
          COALESCE(SUM(credits), 0) FROM course_offerings WHERE offering_id IN (1, 11, 12, 13, 16, 18, 21)
UNION ALL SELECT '고양이 희과담에 웹프01 없음 (0)', COUNT(*) FROM wishlists WHERE student_id = 1 AND offering_id = 18
UNION ALL SELECT '고양이 희과담에 프언02 있음 (1)', COUNT(*) FROM wishlists WHERE student_id = 1 AND offering_id = 10
UNION ALL SELECT '고양이·다람쥐 희과담 겹침 (0)', COUNT(*)
          FROM wishlists a JOIN wishlists b ON a.offering_id = b.offering_id
          WHERE a.student_id = 1 AND b.student_id = 2
UNION ALL SELECT '다람쥐가 프언01 보유 (1)', COUNT(*) FROM enrollments WHERE student_id = 2 AND offering_id = 9
UNION ALL SELECT '졸린고양이 1순위 = 프언01 (1)', COUNT(*) FROM wishlists WHERE student_id = 1 AND offering_id = 9 AND preference_rank = 1
UNION ALL SELECT '다람쥐 1순위 = 웹프01 (1)', COUNT(*) FROM wishlists WHERE student_id = 2 AND offering_id = 18 AND preference_rank = 1
UNION ALL SELECT '다람쥐 희망과목 (1)', COUNT(*) FROM wishlists WHERE student_id = 2
UNION ALL SELECT '다람쥐 수강 (1 = 프언01만)', COUNT(*) FROM enrollments WHERE student_id = 2
UNION ALL SELECT '체험 계정 희망과목 (30)', COUNT(*) FROM wishlists w JOIN students s ON s.student_id = w.student_id
          WHERE s.department = '체험'
UNION ALL SELECT '체험 계정 (10)', COUNT(*) FROM students WHERE department = '체험'
UNION ALL SELECT '체험 계정 수강 (0 = 본인이 직접 신청)', COUNT(*) FROM enrollments e JOIN students s ON s.student_id = e.student_id
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
