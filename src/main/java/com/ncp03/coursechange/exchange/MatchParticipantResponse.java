package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.course.CourseResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "매칭 참여자 1명. 학번은 노출하지 않고 익명 닉네임만 내려준다")
public record MatchParticipantResponse(
        @Schema(description = "이 참여자가 나인지", example = "true") boolean me,
        @Schema(description = "익명 닉네임", example = "바쁜다람쥐") String nickname,
        @Schema(description = "응답 상태. PENDING / ACCEPTED / REJECTED", example = "PENDING") String responseStatus,
        @Schema(description = "주는 분반") CourseResponse giveCourse,
        @Schema(description = "받는 분반") CourseResponse receiveCourse,
        @Schema(description = "보낸 깨우기 횟수 (최대 3)", example = "0") int wakeSentCount
) {
}
