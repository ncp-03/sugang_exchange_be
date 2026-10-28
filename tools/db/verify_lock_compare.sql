-- READ ONLY: LT-07 / LT-08 락 비교 결과 검증 (락 없음 vs 비관적 락)
-- 사용: 각 측정(k6 실행) 직후, 같은 전용 DB에서 실행하고 결과를 기록한다.
--   mysql> SOURCE tools/db/verify_lock_compare.sql;
-- 기대값은 PESSIMISTIC 기준이다. NONE에서 0이 아닌 값이 나오는 것이 "락이 필요한 이유"다.
USE course_change_loadtest;

-- [LT-07-1] 한 요청이 진행 중(PENDING) 세션 2개 이상에 동시에 묶임.  기대: 0행
--  좌석(enrollments)은 @Version·UNIQUE가 지키지만, "요청이 한 세션에만 묶인다"는 DB 제약이 없다.
SELECT p.request_id, COUNT(DISTINCT p.session_id) AS pending_sessions
FROM match_participants p
JOIN match_sessions s ON s.session_id = p.session_id
WHERE s.status = 'PENDING'
GROUP BY p.request_id
HAVING COUNT(DISTINCT p.session_id) > 1;

-- [LT-07-2] 발표용 한 줄 수치: 초과 생성된 세션 수 (요청당 PENDING 세션 수 - 1 의 합).  기대: 0
SELECT COALESCE(SUM(t.cnt - 1), 0) AS excess_pending_sessions
FROM (
    SELECT p.request_id, COUNT(DISTINCT p.session_id) AS cnt
    FROM match_participants p
    JOIN match_sessions s ON s.session_id = p.session_id
    WHERE s.status = 'PENDING'
    GROUP BY p.request_id
    HAVING COUNT(DISTINCT p.session_id) > 1
) t;

-- [LT-07-3] 상태별 세션 수 (참고: 몇 개가 만들어졌는지)
SELECT status, COUNT(*) AS sessions FROM match_sessions GROUP BY status;

-- [LT-08-1] 둘 다 동의했는데 세션이 PENDING으로 남음 (동시 동의 lost update).  기대: 0행
SELECT s.session_id, s.status
FROM match_sessions s
WHERE s.status = 'PENDING'
  AND NOT EXISTS (
      SELECT 1 FROM match_participants p
      WHERE p.session_id = s.session_id AND p.response_status <> 'ACCEPTED'
  );

-- [LT-08-2] 완료된 세션인데 좌석이 정확히 한 번 바뀌지 않음 (받기로 한 분반이 아님).  기대: 0행
SELECT p.session_id, p.student_id, p.receive_offering_id, e.offering_id AS current_offering_id
FROM match_participants p
JOIN match_sessions s ON s.session_id = p.session_id
JOIN enrollments e ON e.enrollment_id = p.offered_enrollment_id
WHERE s.status = 'COMPLETED'
  AND e.offering_id <> p.receive_offering_id;

-- [공통] 교환 후 학점 상한·시간표·정원 불변식은 tools/db/verify_loadtest.sql 의
--        timetable_overlap / credit_limit / capacity 세 쿼리를 이어서 실행한다. 기대: 모두 0