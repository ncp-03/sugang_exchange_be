-- =====================================================================
-- V3: 로그인용 비밀번호 해시 (학번 + 비밀번호 로그인 → 토큰 발급)
--
-- 해시 = SHA2(CONCAT(학번, ':', 비밀번호), 256). 학번이 학생별 salt 역할을 한다.
-- 가상 데이터이므로 초기 비밀번호는 학번과 같게 둔다 (예: 학번 2024000001 → 비밀번호 2024000001).
-- 이미 들어 있는 부하테스트용 대량 학생(course_change_loadtest)도 같은 규칙으로 채워진다.
-- 기존 컬럼·데이터는 건드리지 않고 컬럼 1개만 추가한다.
-- =====================================================================
ALTER TABLE students
    ADD COLUMN password_hash VARCHAR(64) NULL COMMENT 'SHA2(학번:비밀번호, 256) 소문자 16진수. 화면·API에 노출하지 않음';

UPDATE students SET password_hash = SHA2(CONCAT(student_no, ':', student_no), 256) WHERE password_hash IS NULL;

