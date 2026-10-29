-- =====================================================================
-- 시연용 데이터 준비 (수동 실행)
--
-- 흐름: 희망과목 담기 → 수강신청 → 인기 분반은 정원 마감 → 맞교환으로 획득
--
-- 사용:  mysql> USE course_change;  SOURCE tools/db/seed_demo.sql;
--   또는 docker exec -i course-change-mysql mysql -ucourse_change -pcourse_change \
--            course_change < tools/db/seed_demo.sql
--
-- 주의: 시연 DB(course_change) 전용. course_change_loadtest 에는 절대 실행하지 말 것.
--       리허설을 다시 할 때마다 이 파일을 다시 실행하면 처음 상태로 돌아간다.
-- =====================================================================

-- ---------------------------------------------------------------- 1. 초기화
-- 외래 키 때문에 자식 테이블부터 지운다. 강의·학생 데이터는 건드리지 않는다.
DELETE FROM penalty_events;
DELETE FROM match_participants;
DELETE FROM match_sessions;
DELETE FROM exchange_preferences;
DELETE FROM exchange_requests;
DELETE FROM wishlists;

-- 시연 학생(학생 1 / 학번 2024000001 / 졸린고양이)을 "수강신청 전" 상태로 되돌린다.
DELETE FROM enrollments WHERE student_id = 1;

-- ---------------------------------------------------------------- 2. 희망과목 담기
-- 수강신청 화면의 '수강희망과목' 탭에 희망 순위대로 나온다.
INSERT INTO wishlists (student_id, offering_id, preference_rank) VALUES
    (1,  9, 1),   -- 프로그래밍언어론 01 (정원 2명, 이미 마감 → 신청 실패 연출)
    (1, 10, 2),   -- 프로그래밍언어론 02 (여유 → 신청 성공, 나중에 교환으로 내놓을 강의)
    (1,  3, 3),   -- 운영체제 01
    (1,  7, 4),   -- 데이터베이스 01
    (1,  5, 5),   -- 컴퓨터네트워크 01
    (1, 18, 6);   -- 웹프로그래밍 01

-- ---------------------------------------------------------------- 3. 다른 학생들의 교환 요청
-- 강의 검색 화면의 '희망자 / 보유자' 숫자가 이 데이터에서 나온다.
-- 모든 쌍은 학점·시간표·같은 과목 조건을 통과하도록 골랐다.

-- (가) 학생 2 (바쁜다람쥐) : 프로그래밍언어론 01 내놓고 02 희망  ← 시연의 교환 상대
INSERT INTO exchange_requests (student_id, offered_enrollment_id, status, queued_at)
SELECT e.student_id, e.enrollment_id, 'WAITING', NOW() - INTERVAL 30 MINUTE
FROM enrollments e WHERE e.student_id = 2 AND e.offering_id = 9;

INSERT INTO exchange_preferences (request_id, target_offering_id, preference_rank)
SELECT r.request_id, 10, 1
FROM exchange_requests r JOIN enrollments e ON e.enrollment_id = r.offered_enrollment_id
WHERE r.student_id = 2 AND e.offering_id = 9;

-- (나) 학생 3 (용감한펭귄) : 대학영어 02 내놓고 01 희망
INSERT INTO exchange_requests (student_id, offered_enrollment_id, status, queued_at)
SELECT e.student_id, e.enrollment_id, 'WAITING', NOW() - INTERVAL 20 MINUTE
FROM enrollments e WHERE e.student_id = 3 AND e.offering_id = 16;

INSERT INTO exchange_preferences (request_id, target_offering_id, preference_rank)
SELECT r.request_id, 15, 1
FROM exchange_requests r JOIN enrollments e ON e.enrollment_id = r.offered_enrollment_id
WHERE r.student_id = 3 AND e.offering_id = 16;

-- (다) 학생 7 (씩씩한토끼) : 대학영어 02 내놓고 01 희망 (같은 대기열에 경쟁자 1명)
INSERT INTO exchange_requests (student_id, offered_enrollment_id, status, queued_at)
SELECT e.student_id, e.enrollment_id, 'WAITING', NOW() - INTERVAL 10 MINUTE
FROM enrollments e WHERE e.student_id = 7 AND e.offering_id = 16;

INSERT INTO exchange_preferences (request_id, target_offering_id, preference_rank)
SELECT r.request_id, 15, 1
FROM exchange_requests r JOIN enrollments e ON e.enrollment_id = r.offered_enrollment_id
WHERE r.student_id = 7 AND e.offering_id = 16;

-- (라) 학생 8 (조용한부엉이) : 이산수학 02 내놓고 01 희망
INSERT INTO exchange_requests (student_id, offered_enrollment_id, status, queued_at)
SELECT e.student_id, e.enrollment_id, 'WAITING', NOW() - INTERVAL 5 MINUTE
FROM enrollments e WHERE e.student_id = 8 AND e.offering_id = 14;

INSERT INTO exchange_preferences (request_id, target_offering_id, preference_rank)
SELECT r.request_id, 13, 1
FROM exchange_requests r JOIN enrollments e ON e.enrollment_id = r.offered_enrollment_id
WHERE r.student_id = 8 AND e.offering_id = 14;

-- ---------------------------------------------------------------- 4. 확인
SELECT '시연 학생 수강내역 (0이어야 함)' AS check_name, COUNT(*) AS value
FROM enrollments WHERE student_id = 1
UNION ALL
SELECT '시연 학생 희망과목 (6이어야 함)', COUNT(*) FROM wishlists WHERE student_id = 1
UNION ALL
SELECT '대기 중 교환 요청 (4여야 함)', COUNT(*) FROM exchange_requests WHERE status = 'WAITING';

-- 프로그래밍언어론 01분반이 정원 마감인지 (정원 2 / 신청 2)
SELECT o.offering_id, c.course_name, o.section_no, o.capacity,
       COUNT(e.enrollment_id) AS enrolled
FROM course_offerings o
JOIN courses c ON c.course_id = o.course_id
LEFT JOIN enrollments e ON e.offering_id = o.offering_id
WHERE o.offering_id IN (9, 10)
GROUP BY o.offering_id, c.course_name, o.section_no, o.capacity;