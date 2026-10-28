package com.ncp03.coursechange.course;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "검색 필터 선택지 (드롭다운용)")
public record CourseFiltersResponse(
        @Schema(description = "개설 학과 목록") List<String> departments,
        @Schema(description = "교수 목록") List<String> professors,
        @Schema(description = "이수 구분 목록") List<Option> courseTypes,
        @Schema(description = "대상학년 목록") List<Option> targetYears,
        @Schema(description = "요일 목록") List<Option> weekdays
) {
    @Schema(description = "선택지 1개")
    public record Option(
            @Schema(description = "요청에 넣을 값", example = "MAJOR_REQUIRED") String value,
            @Schema(description = "화면 표시", example = "전공필수") String label) {
    }
}
