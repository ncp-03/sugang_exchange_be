package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.course.CourseResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "희망 분반 1건")
public record PreferenceResponse(
        @Schema(description = "우선순위. 1이 가장 원하는 분반", example = "1") int rank,
        @Schema(description = "희망 분반 정보") CourseResponse course
) {
}
