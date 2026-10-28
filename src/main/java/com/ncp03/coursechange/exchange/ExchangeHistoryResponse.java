package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.course.CourseResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내 교환 기록 (완료된 교환만, 최근 먼저)")
public record ExchangeHistoryResponse(
        @Schema(description = "기록 수", example = "2") int count,
        @Schema(description = "기록 목록") List<Item> items
) {
    @Schema(description = "교환 기록 1건")
    public record Item(
            @Schema(description = "매칭 세션 ID", example = "4") Long sessionId,
            @Schema(description = "교환이 끝난 시각", example = "2026-10-27T15:42:10") LocalDateTime completedAt,
            @Schema(description = "상대 익명 닉네임", example = "바쁜다람쥐") String partnerNickname,
            @Schema(description = "내가 준 분반") CourseResponse giveCourse,
            @Schema(description = "내가 받은 분반") CourseResponse receiveCourse
    ) {
    }
}
