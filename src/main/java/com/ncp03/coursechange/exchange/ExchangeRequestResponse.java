package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.course.CourseResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "교환 요청 1건")
public record ExchangeRequestResponse(
        @Schema(description = "요청 ID", example = "7") Long requestId,
        @Schema(description = "상태. WAITING(대기열) / MATCHED(상대 찾음, 동의 대기) / COMPLETED(교환 완료) / CANCELLED(취소)",
                example = "WAITING") String status,
        @Schema(description = "대기열 진입 시각. 대기 시간 가산점의 기준", example = "2026-10-27T15:40:00")
        LocalDateTime queuedAt,
        @Schema(description = "내놓는 수강 내역 ID", example = "3") Long offeredEnrollmentId,
        @Schema(description = "내놓는 분반") CourseResponse offeredCourse,
        @Schema(description = "희망 분반 (우선순위 순)") List<PreferenceResponse> preferences,
        @Schema(description = "진행 중인 매칭 세션 ID. 없으면 null", example = "4") Long matchSessionId
) {
}
