package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.course.CourseResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = """
        교환 쌍 가능 여부 표. 내 수강 내역(내놓을 강의) × 희망 분반(받을 강의)의 모든 칸을 돌려준다.
        화면의 '내 페이지' 표에서 칸마다 가능/불가와 사유를 바로 그릴 수 있다.""")
public record ExchangeEligibilityResponse(
        @Schema(description = "내놓을 수 있는 강의(내 수강 내역)") List<CourseResponse> offeredCourses,
        @Schema(description = "받을 강의 후보 (기본값은 내 찜 목록)") List<CourseResponse> wantedCourses,
        @Schema(description = "칸 목록. offeredEnrollmentId × wantedOfferingId") List<Cell> cells
) {
    @Schema(description = "교환 쌍 1칸")
    public record Cell(
            @Schema(description = "내놓을 수강 내역 ID", example = "3") Long offeredEnrollmentId,
            @Schema(description = "내놓을 분반 ID", example = "10") Long offeredOfferingId,
            @Schema(description = "받을 분반 ID", example = "9") Long wantedOfferingId,
            @Schema(description = "교환할 수 있는지", example = "true") boolean allowed,
            @Schema(description = "불가 사유 코드. 가능하면 null", example = "SCHEDULE_CONFLICT") String reasonCode,
            @Schema(description = "화면에 보여 줄 짧은 사유. 가능하면 null", example = "시간표 겹침") String reason,
            @Schema(description = "교환하면 바뀌는 총 수강 학점. 불가여도 계산해 준다", example = "16") int creditsAfterSwap
    ) {
    }
}
