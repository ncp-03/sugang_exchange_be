-- READ ONLY: fixture counts and seven invariant checks.
USE course_change_loadtest;

SELECT 'students' table_name,COUNT(*) actual_rows,10010 expected_rows FROM `students`
UNION ALL
SELECT 'courses' table_name,COUNT(*) actual_rows,1213 expected_rows FROM `courses`
UNION ALL
SELECT 'course_offerings' table_name,COUNT(*) actual_rows,2421 expected_rows FROM `course_offerings`
UNION ALL
SELECT 'class_times' table_name,COUNT(*) actual_rows,4840 expected_rows FROM `class_times`
UNION ALL
SELECT 'enrollments' table_name,COUNT(*) actual_rows,60045 expected_rows FROM `enrollments`
UNION ALL
SELECT 'wishlists' table_name,COUNT(*) actual_rows,30000 expected_rows FROM `wishlists`
UNION ALL
SELECT 'exchange_requests' table_name,COUNT(*) actual_rows,10000 expected_rows FROM `exchange_requests`
UNION ALL
SELECT 'exchange_preferences' table_name,COUNT(*) actual_rows,10000 expected_rows FROM `exchange_preferences`;

-- timetable_overlap: expected n = 0
SELECT COUNT(*) n FROM enrollments a JOIN enrollments b ON a.student_id=b.student_id AND a.enrollment_id<b.enrollment_id JOIN class_times x ON x.offering_id=a.offering_id JOIN class_times y ON y.offering_id=b.offering_id WHERE x.weekday=y.weekday AND x.start_time<y.end_time AND y.start_time<x.end_time;

-- credit_limit: expected n = 0
SELECT COUNT(*) n FROM (SELECT e.student_id FROM enrollments e JOIN students s ON s.student_id=e.student_id JOIN course_offerings o ON o.offering_id=e.offering_id GROUP BY e.student_id,s.max_credits HAVING SUM(o.credits)>s.max_credits) v;

-- capacity: expected n = 0
SELECT COUNT(*) n FROM (SELECT o.offering_id FROM course_offerings o JOIN enrollments e ON e.offering_id=o.offering_id GROUP BY o.offering_id,o.capacity HAVING COUNT(*)>o.capacity) v;

-- request_ownership: expected n = 0
SELECT COUNT(*) n FROM exchange_requests r JOIN enrollments e ON e.enrollment_id=r.offered_enrollment_id WHERE r.student_id<>e.student_id;

-- target_same_course_different_section: expected n = 0
SELECT COUNT(*) n FROM exchange_requests r JOIN enrollments e ON e.enrollment_id=r.offered_enrollment_id JOIN exchange_preferences p ON p.request_id=r.request_id JOIN course_offerings o ON o.offering_id=p.target_offering_id WHERE r.request_id>=100001 AND (e.course_id<>o.course_id OR e.offering_id=o.offering_id);

-- missing_reciprocal_partner: expected n = 0
SELECT COUNT(*) n FROM exchange_requests r JOIN enrollments e ON e.enrollment_id=r.offered_enrollment_id JOIN exchange_preferences p ON p.request_id=r.request_id WHERE r.request_id>=100001 AND NOT EXISTS (SELECT 1 FROM enrollments other JOIN exchange_requests rr ON rr.offered_enrollment_id=other.enrollment_id JOIN exchange_preferences pp ON pp.request_id=rr.request_id WHERE other.offering_id=p.target_offering_id AND pp.target_offering_id=e.offering_id AND other.student_id<>e.student_id);

-- wishlist_already_enrolled: expected n = 0
SELECT COUNT(*) n FROM wishlists w JOIN enrollments e ON e.student_id=w.student_id AND e.offering_id=w.offering_id;
