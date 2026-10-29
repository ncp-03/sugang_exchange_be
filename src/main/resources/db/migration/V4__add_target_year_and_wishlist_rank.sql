-- =====================================================================
-- V4: 화면에 필요한 두 가지를 DB에 추가한다.
--
--  1. courses.target_year     : 강의 검색의 '대상학년' 표시·필터용
--  2. wishlists.preference_rank : 찜 목록의 '희망 순위'(1이 가장 원함) 저장용
--
-- 적용된 V1~V3은 건드리지 않고 컬럼만 추가한다 (Flyway 체크섬 보호).
-- =====================================================================

-- ---------------------------------------------------------------- 대상학년
ALTER TABLE courses
    ADD COLUMN target_year TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '대상학년 (1~6)';

-- 학수번호의 첫 숫자를 학년으로 본다. 예: CSE3050 → 3학년, GEN1010 → 1학년.
-- 숫자를 찾지 못하면 1학년으로 둔다.
UPDATE courses
SET target_year = CASE
        WHEN REGEXP_SUBSTR(course_code, '[1-6]') IS NULL THEN 1
        ELSE CAST(REGEXP_SUBSTR(course_code, '[1-6]') AS UNSIGNED)
    END;

ALTER TABLE courses
    ADD CONSTRAINT ck_courses_target_year CHECK (target_year BETWEEN 1 AND 6);

-- ---------------------------------------------------------------- 찜 희망 순위
ALTER TABLE wishlists
    ADD COLUMN preference_rank INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '희망 순위. 1이 가장 원함, 0은 아직 순위 없음';

-- 기존 찜은 먼저 찜한 것부터 1, 2, 3... 순으로 매긴다 (학생별).
UPDATE wishlists w
JOIN (
    SELECT wishlist_id,
           ROW_NUMBER() OVER (PARTITION BY student_id ORDER BY created_at, wishlist_id) AS rn
    FROM wishlists
) t ON t.wishlist_id = w.wishlist_id
SET w.preference_rank = t.rn;

CREATE INDEX idx_wishlists_student_rank ON wishlists (student_id, preference_rank);
