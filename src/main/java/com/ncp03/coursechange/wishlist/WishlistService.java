package com.ncp03.coursechange.wishlist;

import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.course.CourseAssembler;
import com.ncp03.coursechange.course.CourseResponse;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.CourseOfferingRepository;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.Student;
import com.ncp03.coursechange.domain.Wishlist;
import com.ncp03.coursechange.domain.WishlistRepository;
import com.ncp03.coursechange.student.StudentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;

/**
 * 찜(하트·장바구니).
 * 관심 목록이라 학점 상한이나 시간표 겹침으로 막지 않는다.
 * 다만 "이미 수강 중인 분반"은 교환 희망 대상이 될 수 없으므로 찜 추가를 거절한다.
 */
@Service
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final CourseOfferingRepository offeringRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentService studentService;
    private final CourseAssembler courseAssembler;

    public WishlistService(WishlistRepository wishlistRepository,
                           CourseOfferingRepository offeringRepository,
                           EnrollmentRepository enrollmentRepository,
                           StudentService studentService,
                           CourseAssembler courseAssembler) {
        this.wishlistRepository = wishlistRepository;
        this.offeringRepository = offeringRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentService = studentService;
        this.courseAssembler = courseAssembler;
    }

    public MyWishlistsResponse myWishlists(Long studentId) {
        Student student = studentService.getStudent(studentId);
        List<Wishlist> wishlists = wishlistRepository.findAllByStudentIdWithOffering(studentId);
        List<CourseResponse> courses = courseAssembler.toResponses(
                wishlists.stream().map(Wishlist::getOffering).toList());

        List<WishlistItemResponse> items = new ArrayList<>();
        for (int i = 0; i < wishlists.size(); i++) {
            Wishlist w = wishlists.get(i);
            CourseResponse course = courses.get(i);
            boolean enrolled = enrollmentRepository
                    .existsByStudentIdAndOfferingId(studentId, w.getOffering().getId());
            items.add(new WishlistItemResponse(w.getId(), w.getPreferenceRank(), w.getCreatedAt(), enrolled, course));
        }
        return new MyWishlistsResponse(student.getId(), items.size(), items);
    }

    @Transactional
    public WishlistItemResponse add(Long studentId, Long offeringId) {
        Student student = studentService.getStudent(studentId);
        CourseOffering offering = offeringRepository.findWithCourseById(offeringId)
                .orElseThrow(() -> new ApiException(ErrorCode.COURSE_NOT_FOUND));

        // 내가 지금 듣고 있는 바로 그 분반은 찜할 이유가 없다.
        // 같은 과목의 '다른' 분반은 찜의 핵심 용도이므로 막지 않는다 (교환으로 받을 대상).
        if (enrollmentRepository.existsByStudentIdAndOfferingId(studentId, offeringId)) {
            throw new ApiException(ErrorCode.ALREADY_ENROLLED,
                    "'" + offering.getCourse().getCourseName() + " " + offering.getSectionNo()
                            + "분반'은 이미 수강 중입니다.");
        }
        if (wishlistRepository.existsByStudentIdAndOfferingId(studentId, offeringId)) {
            throw new ApiException(ErrorCode.WISHLIST_ALREADY_EXISTS);
        }

        Wishlist saved;
        try {
            // UNIQUE(student_id, offering_id)가 최종 방어선. 동시에 두 번 눌러도 1건만 남는다.
            // 새로 찜한 분반은 희망 순위의 맨 뒤로 들어간다. 순서는 PATCH /api/wishlists/order 로 바꾼다.
            int nextRank = wishlistRepository.findMaxRankByStudentId(studentId) + 1;
            saved = wishlistRepository.saveAndFlush(Wishlist.of(student, offering, nextRank));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.WISHLIST_ALREADY_EXISTS);
        }
        return new WishlistItemResponse(saved.getId(), saved.getPreferenceRank(), saved.getCreatedAt(), false,
                courseAssembler.toResponse(offering));
    }

    @Transactional
    public void remove(Long studentId, Long offeringId) {
        studentService.getStudent(studentId);
        Wishlist wishlist = wishlistRepository.findByStudentIdAndOfferingId(studentId, offeringId)
                .orElseThrow(() -> new ApiException(ErrorCode.WISHLIST_NOT_FOUND));
        wishlistRepository.delete(wishlist);
        // 지운 자리를 메워 1, 2, 3... 이 이어지게 다시 매긴다.
        renumber(studentId, wishlist.getId());
    }

    /**
     * 희망 순위를 통째로 바꾼다. 화면의 찜 목록 순서를 그대로 보내면 된다 (첫 번째가 1순위).
     * 목록이 서버와 어긋나면(사이에 찜을 추가·삭제했다면) 400으로 거절하고 다시 불러오게 한다.
     */
    @Transactional
    public MyWishlistsResponse reorder(Long studentId, List<Long> offeringIds) {
        studentService.getStudent(studentId);
        List<Wishlist> wishlists = wishlistRepository.findAllByStudentIdWithOffering(studentId);
        Map<Long, Wishlist> byOffering = new HashMap<>();
        for (Wishlist w : wishlists) {
            byOffering.put(w.getOffering().getId(), w);
        }
        if (new HashSet<>(offeringIds).size() != offeringIds.size()) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "offeringIds에 같은 분반이 두 번 들어 있습니다.");
        }
        if (offeringIds.size() != wishlists.size() || !byOffering.keySet().containsAll(offeringIds)) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER,
                    "찜 목록 전체를 순서대로 보내 주세요. 현재 찜은 " + wishlists.size() + "개입니다.");
        }
        int rank = 1;
        for (Long offeringId : offeringIds) {
            byOffering.get(offeringId).changeRank(rank++);
        }
        return myWishlists(studentId);
    }

    /** 남은 찜의 순위를 1부터 다시 매긴다. */
    private void renumber(Long studentId, Long removedId) {
        int rank = 1;
        for (Wishlist w : wishlistRepository.findAllByStudentIdWithOffering(studentId)) {
            if (!w.getId().equals(removedId)) {
                w.changeRank(rank++);
            }
        }
    }
}
