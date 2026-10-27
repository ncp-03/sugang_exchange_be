package com.ncp03.coursechange.course;

import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.CourseOfferingRepository;
import com.ncp03.coursechange.domain.CourseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseOfferingRepository offeringRepository;
    private final CourseAssembler assembler;

    public CourseService(CourseOfferingRepository offeringRepository, CourseAssembler assembler) {
        this.offeringRepository = offeringRepository;
        this.assembler = assembler;
    }

    public PageResponse<CourseResponse> search(String keyword, String department, CourseType courseType,
                                               Integer weekday, int page, int size) {
        Page<CourseOffering> result = offeringRepository.search(
                toLikePattern(keyword), blankToNull(department), courseType, weekday, PageRequest.of(page, size));
        return PageResponse.of(result, assembler.toResponses(result.getContent()));
    }

    public CourseResponse get(Long offeringId) {
        CourseOffering offering = offeringRepository.findWithCourseById(offeringId)
                .orElseThrow(() -> new ApiException(ErrorCode.COURSE_NOT_FOUND));
        return assembler.toResponse(offering);
    }

    public CourseFiltersResponse filters() {
        List<CourseFiltersResponse.Option> types = Arrays.stream(CourseType.values())
                .map(t -> new CourseFiltersResponse.Option(t.name(), t.getLabel()))
                .toList();
        List<CourseFiltersResponse.Option> weekdays = IntStream.rangeClosed(1, 5)
                .mapToObj(d -> new CourseFiltersResponse.Option(String.valueOf(d), Weekdays.label(d)))
                .toList();
        return new CourseFiltersResponse(offeringRepository.findAllDepartments(), types, weekdays);
    }

    /** LIKE 특수문자(%, _, \)는 글자 그대로 검색되도록 이스케이프한다. */
    static String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
