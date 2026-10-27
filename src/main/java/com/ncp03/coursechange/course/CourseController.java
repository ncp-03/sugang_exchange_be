package com.ncp03.coursechange.course;

import com.ncp03.coursechange.common.error.ErrorResponse;
import com.ncp03.coursechange.domain.CourseType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "강의", description = "가상 학사 시스템의 강의(개설 분반) 조회")
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @Operation(summary = "강의 목록·검색",
            description = "모든 조건은 선택입니다. 학수번호 → 분반 순으로 정렬됩니다. 로그인 없이 호출할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "400", description = "파라미터 오류 (INVALID_PARAMETER)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<CourseResponse> search(
            @Parameter(description = "과목명·학수번호·교수명 검색어 (부분 일치)", example = "프로그래밍")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "개설 학과 (정확히 일치)", example = "컴퓨터공학과")
            @RequestParam(required = false) String department,
            @Parameter(description = "이수 구분 코드")
            @RequestParam(required = false) CourseType courseType,
            @Parameter(description = "이 요일에 수업이 있는 강의 (1=월 ... 5=금)", example = "2")
            @RequestParam(required = false) @Min(1) @Max(5) Integer weekday,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return courseService.search(keyword, department, courseType, weekday, page, size);
    }

    @Operation(summary = "검색 필터 선택지", description = "학과·이수 구분·요일 드롭다운에 쓸 값입니다.")
    @GetMapping("/filters")
    public CourseFiltersResponse filters() {
        return courseService.filters();
    }

    @Operation(summary = "강의 상세", description = "분반 하나의 정보·수업 시간·교환 현황")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "없는 분반 (COURSE_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{offeringId}")
    public CourseResponse get(
            @Parameter(description = "분반 ID", example = "9") @PathVariable @Min(1) Long offeringId) {
        return courseService.get(offeringId);
    }
}
