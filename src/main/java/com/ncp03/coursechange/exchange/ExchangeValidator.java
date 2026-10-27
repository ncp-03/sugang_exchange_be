package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.domain.ClassTime;
import com.ncp03.coursechange.domain.ClassTimeRepository;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.Enrollment;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.Student;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "이 좌석을 내놓고 저 분반을 받는 교환이 성립 가능한가"를 한곳에서 검사한다.
 *
 * <p>교환 요청 등록 시점(희망 분반이 애초에 불가능한지)과 매칭 시점(상대 후보를 고를 때),
 * 그리고 교환 실행 직전(그동안 시간표가 바뀌었을 수 있으므로) 세 번 쓰인다.
 *
 * <p>검사 항목은 4개다.
 * <ol>
 *   <li>같은 분반인지 — 분반 ID가 같으면 교환이 아니다 (같은 과목 다른 분반은 허용).</li>
 *   <li>같은 과목 중복 — 받을 과목의 다른 분반을 이미 듣고 있으면 UNIQUE(student_id, course_id) 위반.</li>
 *   <li>학점 상한 — (현재 학점 − 내놓는 학점 + 받는 학점) ≤ max_credits.</li>
 *   <li>시간표 겹침 — 내놓는 분반을 뺀 나머지 수강 분반과 받을 분반의 수업 시간이 겹치면 안 된다.</li>
 * </ol>
 */
@Component
public class ExchangeValidator {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassTimeRepository classTimeRepository;

    public ExchangeValidator(EnrollmentRepository enrollmentRepository, ClassTimeRepository classTimeRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.classTimeRepository = classTimeRepository;
    }

    /** 통과하면 비어 있는 Optional, 실패하면 이유 코드. 매칭 후보를 거를 때 이 형태가 편하다. */
    public Optional<ErrorCode> check(Student student, Enrollment offered, CourseOffering receive) {
        Long studentId = student.getId();

        // 1. 같은 분반이면 교환이 아니다.
        if (offered.getOffering().getId().equals(receive.getId())) {
            return Optional.of(ErrorCode.PREFERENCE_SAME_AS_OFFERED);
        }

        // 2. 받을 과목의 다른 분반을 이미 듣고 있으면 안 된다.
        //    단, 내놓는 좌석이 바로 그 과목이면(= 같은 과목 다른 분반 교환) 괜찮다.
        Long receiveCourseId = receive.getCourse().getId();
        if (!receiveCourseId.equals(offered.getCourseId())
                && enrollmentRepository.existsByStudentIdAndCourseId(studentId, receiveCourseId)) {
            return Optional.of(ErrorCode.SAME_COURSE_ALREADY_HELD);
        }

        // 내 현재 수강 내역 (내놓는 좌석 제외)
        List<Enrollment> others = enrollmentRepository.findAllByStudentIdWithOffering(studentId).stream()
                .filter(e -> !e.getId().equals(offered.getId()))
                .toList();

        // 3. 학점 상한
        int otherCredits = others.stream().mapToInt(e -> e.getOffering().getCredits()).sum();
        int after = otherCredits + receive.getCredits();
        if (after > student.getMaxCredits()) {
            return Optional.of(ErrorCode.CREDIT_LIMIT_EXCEEDED);
        }

        // 4. 시간표 겹침
        if (hasScheduleConflict(others, receive)) {
            return Optional.of(ErrorCode.SCHEDULE_CONFLICT);
        }
        return Optional.empty();
    }

    /** 실패하면 ApiException을 던진다. 사용자가 직접 보낸 값을 검사할 때 쓴다. */
    public void validateOrThrow(Student student, Enrollment offered, CourseOffering receive) {
        check(student, offered, receive).ifPresent(code -> {
            throw new ApiException(code, messageFor(code, receive));
        });
    }

    /** 교환 후 학점. 화면에 "교환하면 15 → 16학점" 을 보여 주려고 노출한다. */
    public int creditsAfterSwap(Long studentId, Enrollment offered, CourseOffering receive) {
        int others = enrollmentRepository.findAllByStudentIdWithOffering(studentId).stream()
                .filter(e -> !e.getId().equals(offered.getId()))
                .mapToInt(e -> e.getOffering().getCredits())
                .sum();
        return others + receive.getCredits();
    }

    private boolean hasScheduleConflict(List<Enrollment> others, CourseOffering receive) {
        if (others.isEmpty()) {
            return false;
        }
        List<Long> otherOfferingIds = others.stream().map(e -> e.getOffering().getId()).distinct().toList();

        List<Long> idsToLoad = new ArrayList<>(otherOfferingIds);
        idsToLoad.add(receive.getId());
        List<ClassTime> all = classTimeRepository.findByOfferingIds(idsToLoad);

        List<ClassTime> receiveTimes = all.stream()
                .filter(t -> t.getOffering().getId().equals(receive.getId()))
                .toList();
        List<ClassTime> otherTimes = all.stream()
                .filter(t -> !t.getOffering().getId().equals(receive.getId()))
                .toList();

        for (ClassTime r : receiveTimes) {
            for (ClassTime o : otherTimes) {
                if (r.overlaps(o)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String messageFor(ErrorCode code, CourseOffering receive) {
        String label = receive.getCourse().getCourseName() + " " + receive.getSectionNo() + "분반";
        return switch (code) {
            case PREFERENCE_SAME_AS_OFFERED -> "내놓는 분반(" + label + ")을 희망 분반으로 넣을 수 없습니다.";
            case SAME_COURSE_ALREADY_HELD -> "'" + receive.getCourse().getCourseName()
                    + "' 과목의 다른 분반을 이미 수강 중입니다.";
            case CREDIT_LIMIT_EXCEEDED -> label + "로 교환하면 신청 가능 최대 학점을 넘습니다.";
            case SCHEDULE_CONFLICT -> label + "은 지금 듣는 다른 수업과 시간이 겹칩니다.";
            default -> code.getDefaultMessage();
        };
    }
}
