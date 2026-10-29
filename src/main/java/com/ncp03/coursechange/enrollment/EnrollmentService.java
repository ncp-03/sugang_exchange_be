package com.ncp03.coursechange.enrollment;

import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.course.CourseAssembler;
import com.ncp03.coursechange.course.CourseResponse;
import com.ncp03.coursechange.domain.ClassTime;
import com.ncp03.coursechange.domain.ClassTimeRepository;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.CourseOfferingRepository;
import com.ncp03.coursechange.domain.Enrollment;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.ExchangeRequestRepository;
import com.ncp03.coursechange.domain.RequestStatus;
import com.ncp03.coursechange.domain.Student;
import com.ncp03.coursechange.student.StudentService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EnrollmentService {

    private static final List<RequestStatus> ACTIVE =
            List.of(RequestStatus.WAITING, RequestStatus.MATCHED);

    private final EnrollmentRepository enrollmentRepository;
    private final StudentService studentService;
    private final CourseAssembler courseAssembler;
    private final CourseOfferingRepository offeringRepository;
    private final ClassTimeRepository classTimeRepository;
    private final ExchangeRequestRepository requestRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository, StudentService studentService,
                             CourseAssembler courseAssembler, CourseOfferingRepository offeringRepository,
                             ClassTimeRepository classTimeRepository,
                             ExchangeRequestRepository requestRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentService = studentService;
        this.courseAssembler = courseAssembler;
        this.offeringRepository = offeringRepository;
        this.classTimeRepository = classTimeRepository;
        this.requestRepository = requestRepository;
    }

    public MyEnrollmentsResponse myEnrollments(Long studentId) {
        Student student = studentService.getStudent(studentId);
        List<Enrollment> enrollments = enrollmentRepository.findAllByStudentIdWithOffering(studentId);
        List<CourseResponse> courses = courseAssembler.toResponses(
                enrollments.stream().map(Enrollment::getOffering).toList());

        List<EnrollmentItemResponse> items = new ArrayList<>();
        long totalCredits = 0;
        for (int i = 0; i < enrollments.size(); i++) {
            Enrollment e = enrollments.get(i);
            CourseResponse course = courses.get(i);
            items.add(new EnrollmentItemResponse(e.getId(), e.getEnrolledAt(), course));
            totalCredits += course.credits();
        }
        return new MyEnrollmentsResponse(student.getId(), student.getNickname(), totalCredits,
                student.getMaxCredits(), items.size(), items);
    }

    // --------------------------------------------------------------- 수강신청

    /**
     * 수강신청. 교환과 같은 조건을 검사한다.
     * 정원·중복·같은 과목 중복·학점 상한·시간표 겹침을 차례로 확인한다.
     *
     * <p>정원은 조회 시점 기준이라 동시에 신청하면 정원을 살짝 넘을 수 있다.
     * 모의 서비스라 여기까지만 막고, 좌석 경쟁 자체는 교환 기능으로 푼다.
     */
    @Transactional
    public EnrollmentItemResponse enroll(Long studentId, Long offeringId) {
        Student student = studentService.getStudent(studentId);
        CourseOffering offering = offeringRepository.findWithCourseById(offeringId)
                .orElseThrow(() -> new ApiException(ErrorCode.COURSE_NOT_FOUND));

        if (enrollmentRepository.existsByStudentIdAndOfferingId(studentId, offeringId)) {
            throw new ApiException(ErrorCode.ALREADY_ENROLLED);
        }
        Long courseId = offering.getCourse().getId();
        if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new ApiException(ErrorCode.SAME_COURSE_ALREADY_HELD);
        }

        long enrolledCount = countEnrolled(offeringId);
        if (enrolledCount >= offering.getCapacity()) {
            throw new ApiException(ErrorCode.COURSE_FULL,
                    "'" + offering.getCourse().getCourseName() + " " + offering.getSectionNo()
                            + "분반'은 정원 " + offering.getCapacity() + "명이 모두 찼습니다.");
        }

        List<Enrollment> mine = enrollmentRepository.findAllByStudentIdWithOffering(studentId);
        int credits = mine.stream().mapToInt(e -> e.getOffering().getCredits()).sum() + offering.getCredits();
        if (credits > student.getMaxCredits()) {
            throw new ApiException(ErrorCode.CREDIT_LIMIT_EXCEEDED,
                    "신청하면 " + credits + "학점이 되어 최대 " + student.getMaxCredits() + "학점을 넘습니다.");
        }
        if (hasScheduleConflict(mine, offering)) {
            throw new ApiException(ErrorCode.SCHEDULE_CONFLICT);
        }

        Enrollment saved;
        try {
            // UNIQUE(student_id, offering_id) / (student_id, course_id)가 최종 방어선이다.
            saved = enrollmentRepository.saveAndFlush(Enrollment.of(student, offering));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.ALREADY_ENROLLED);
        }
        return new EnrollmentItemResponse(saved.getId(), saved.getEnrolledAt(),
                courseAssembler.toResponse(offering));
    }

    /** 수강삭제. 교환 요청이 걸려 있는 좌석은 먼저 요청을 취소해야 한다. */
    @Transactional
    public void cancel(Long studentId, Long enrollmentId) {
        studentService.getStudent(studentId);
        Enrollment enrollment = enrollmentRepository.findDetailById(enrollmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENROLLMENT_NOT_FOUND));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new ApiException(ErrorCode.NOT_MY_ENROLLMENT);
        }
        if (requestRepository.existsActiveByEnrollmentId(enrollmentId, ACTIVE)) {
            throw new ApiException(ErrorCode.ENROLLMENT_IN_EXCHANGE);
        }
        enrollmentRepository.delete(enrollment);
    }

    private long countEnrolled(Long offeringId) {
        List<Object[]> rows = enrollmentRepository.countByOfferingIds(List.of(offeringId));
        return rows.isEmpty() ? 0 : ((Number) rows.get(0)[1]).longValue();
    }

    /** 이미 듣는 강의와 수업 시간이 겹치는지. */
    private boolean hasScheduleConflict(List<Enrollment> mine, CourseOffering target) {
        if (mine.isEmpty()) {
            return false;
        }
        List<Long> ids = new ArrayList<>(mine.stream().map(e -> e.getOffering().getId()).distinct().toList());
        ids.add(target.getId());
        List<ClassTime> all = classTimeRepository.findByOfferingIds(ids);
        List<ClassTime> targetTimes = all.stream()
                .filter(t -> t.getOffering().getId().equals(target.getId()))
                .toList();
        for (ClassTime t : targetTimes) {
            for (ClassTime o : all) {
                if (!o.getOffering().getId().equals(target.getId()) && t.overlaps(o)) {
                    return true;
                }
            }
        }
        return false;
    }
}
