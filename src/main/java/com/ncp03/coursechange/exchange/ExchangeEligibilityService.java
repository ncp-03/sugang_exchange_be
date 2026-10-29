package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.course.CourseAssembler;
import com.ncp03.coursechange.course.CourseResponse;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.CourseOfferingRepository;
import com.ncp03.coursechange.domain.Enrollment;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.Student;
import com.ncp03.coursechange.domain.Wishlist;
import com.ncp03.coursechange.domain.WishlistRepository;
import com.ncp03.coursechange.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "이 강의를 내놓고 저 강의를 받을 수 있나"를 화면이 미리 보여 줄 수 있게 표로 만들어 준다.
 *
 * <p>판단 기준은 {@link ExchangeValidator} 하나뿐이다. 교환 요청 등록·매칭·확정 때 쓰는 검사와
 * 같은 코드를 쓰므로 화면과 서버의 판단이 어긋나지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ExchangeEligibilityService {

    private final StudentService studentService;
    private final EnrollmentRepository enrollmentRepository;
    private final WishlistRepository wishlistRepository;
    private final CourseOfferingRepository offeringRepository;
    private final ExchangeValidator validator;
    private final CourseAssembler courseAssembler;

    public ExchangeEligibilityService(StudentService studentService,
                                      EnrollmentRepository enrollmentRepository,
                                      WishlistRepository wishlistRepository,
                                      CourseOfferingRepository offeringRepository,
                                      ExchangeValidator validator,
                                      CourseAssembler courseAssembler) {
        this.studentService = studentService;
        this.enrollmentRepository = enrollmentRepository;
        this.wishlistRepository = wishlistRepository;
        this.offeringRepository = offeringRepository;
        this.validator = validator;
        this.courseAssembler = courseAssembler;
    }

    /**
     * @param wantedOfferingIds 받을 분반 후보. 비어 있으면 내 찜 목록(희망 순위 순)을 쓴다.
     */
    public ExchangeEligibilityResponse eligibility(Long studentId, List<Long> wantedOfferingIds) {
        Student student = studentService.getStudent(studentId);
        List<Enrollment> enrollments = enrollmentRepository.findAllByStudentIdWithOffering(studentId);
        List<CourseOffering> wanted = resolveWanted(studentId, wantedOfferingIds);

        List<CourseResponse> offeredCourses = courseAssembler.toResponses(
                enrollments.stream().map(Enrollment::getOffering).toList());
        List<CourseResponse> wantedCourses = courseAssembler.toResponses(wanted);

        List<ExchangeEligibilityResponse.Cell> cells = new ArrayList<>();
        for (Enrollment offered : enrollments) {
            for (CourseOffering receive : wanted) {
                Optional<ErrorCode> reason = validator.check(student, offered, receive);
                cells.add(new ExchangeEligibilityResponse.Cell(
                        offered.getId(),
                        offered.getOffering().getId(),
                        receive.getId(),
                        reason.isEmpty(),
                        reason.map(Enum::name).orElse(null),
                        reason.map(ExchangeEligibilityService::label).orElse(null),
                        validator.creditsAfterSwap(studentId, offered, receive)));
            }
        }
        return new ExchangeEligibilityResponse(offeredCourses, wantedCourses, cells);
    }

    /** 요청한 분반 ID 목록을 그대로, 없으면 내 찜 목록을 희망 순위 순으로 돌려준다. */
    private List<CourseOffering> resolveWanted(Long studentId, List<Long> wantedOfferingIds) {
        if (wantedOfferingIds == null || wantedOfferingIds.isEmpty()) {
            return wishlistRepository.findAllByStudentIdWithOffering(studentId).stream()
                    .map(Wishlist::getOffering)
                    .toList();
        }
        // 중복을 지우고 보낸 순서를 지킨다.
        Map<Long, CourseOffering> found = new LinkedHashMap<>();
        for (Long id : wantedOfferingIds) {
            if (found.containsKey(id)) {
                continue;
            }
            found.put(id, offeringRepository.findWithCourseById(id)
                    .orElseThrow(() -> new ApiException(ErrorCode.COURSE_NOT_FOUND)));
        }
        return List.copyOf(found.values());
    }

    /** 화면 칸에 한 줄로 보여 줄 사유. */
    private static String label(ErrorCode code) {
        return switch (code) {
            case PREFERENCE_SAME_AS_OFFERED -> "같은 분반";
            case SAME_COURSE_ALREADY_HELD -> "같은 과목을 이미 수강 중";
            case CREDIT_LIMIT_EXCEEDED -> "학점 상한 초과";
            case SCHEDULE_CONFLICT -> "시간표 겹침";
            default -> code.getDefaultMessage();
        };
    }
}
