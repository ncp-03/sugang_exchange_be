-- READ ONLY. Run only after the unchanged application applies Flyway V1 then V2.
-- Expected at first deployment: 11 application tables + flyway_schema_history.
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = 'course_change'
ORDER BY table_name;

SELECT installed_rank, version, description, type, script, checksum,
       installed_on, execution_time, success
FROM course_change.flyway_schema_history
ORDER BY installed_rank;

SELECT 'students' AS table_name, COUNT(*) AS actual_rows, 10 AS expected_rows,
       COUNT(*) = 10 AS matches_initial_seed FROM course_change.students
UNION ALL SELECT 'courses', COUNT(*), 13, COUNT(*) = 13 FROM course_change.courses
UNION ALL SELECT 'course_offerings', COUNT(*), 21, COUNT(*) = 21 FROM course_change.course_offerings
UNION ALL SELECT 'class_times', COUNT(*), 40, COUNT(*) = 40 FROM course_change.class_times
UNION ALL SELECT 'enrollments', COUNT(*), 45, COUNT(*) = 45 FROM course_change.enrollments
UNION ALL SELECT 'wishlists', COUNT(*), 0, COUNT(*) = 0 FROM course_change.wishlists
UNION ALL SELECT 'exchange_requests', COUNT(*), 0, COUNT(*) = 0 FROM course_change.exchange_requests
UNION ALL SELECT 'exchange_preferences', COUNT(*), 0, COUNT(*) = 0 FROM course_change.exchange_preferences
UNION ALL SELECT 'match_sessions', COUNT(*), 0, COUNT(*) = 0 FROM course_change.match_sessions
UNION ALL SELECT 'match_participants', COUNT(*), 0, COUNT(*) = 0 FROM course_change.match_participants
UNION ALL SELECT 'penalty_events', COUNT(*), 0, COUNT(*) = 0 FROM course_change.penalty_events;

-- The following three checks come from upstream SchemaIntegrationTest.
-- All violation counts must be zero for the approved initial sample dataset.
SELECT COUNT(*) AS timetable_overlap_count
FROM course_change.enrollments e1
JOIN course_change.enrollments e2
  ON e1.student_id = e2.student_id AND e1.enrollment_id < e2.enrollment_id
JOIN course_change.class_times t1 ON t1.offering_id = e1.offering_id
JOIN course_change.class_times t2 ON t2.offering_id = e2.offering_id
WHERE t1.weekday = t2.weekday
  AND t1.start_time < t2.end_time AND t2.start_time < t1.end_time;

SELECT COUNT(*) AS credit_limit_violation_count
FROM (
  SELECT s.student_id
  FROM course_change.students s
  JOIN course_change.enrollments e ON e.student_id = s.student_id
  JOIN course_change.course_offerings o ON o.offering_id = e.offering_id
  GROUP BY s.student_id, s.max_credits
  HAVING SUM(o.credits) > s.max_credits
) violations;

SELECT COUNT(*) AS capacity_violation_count
FROM (
  SELECT o.offering_id
  FROM course_change.course_offerings o
  JOIN course_change.enrollments e ON e.offering_id = o.offering_id
  GROUP BY o.offering_id, o.capacity
  HAVING COUNT(*) > o.capacity
) violations;

-- Demo fixtures: student 1 = 5 enrollments / 15 credits;
-- student 8 = 6 enrollments / 18 credits; offering 9 = 2 of 2 seats filled.
SELECT s.student_id, s.nickname, COUNT(e.enrollment_id) AS enrollment_count,
       SUM(o.credits) AS enrolled_credits, s.max_credits
FROM course_change.students s
JOIN course_change.enrollments e ON e.student_id = s.student_id
JOIN course_change.course_offerings o ON o.offering_id = e.offering_id
WHERE s.student_id IN (1, 8)
GROUP BY s.student_id, s.nickname, s.max_credits
ORDER BY s.student_id;

SELECT o.offering_id, o.capacity, COUNT(e.enrollment_id) AS enrolled_count
FROM course_change.course_offerings o
LEFT JOIN course_change.enrollments e ON e.offering_id = o.offering_id
WHERE o.offering_id = 9
GROUP BY o.offering_id, o.capacity;
