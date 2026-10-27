package com.ncp03.coursechange.exchange;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "빠른 매칭 결과. 상대를 못 찾는 것은 정상이므로 404가 아니라 200 + matched=false로 응답한다")
public record QuickMatchResponse(
        @Schema(description = "상대를 찾았는지", example = "true") boolean matched,
        @Schema(description = "못 찾은 이유 코드. matched=true면 null. NO_COUNTERPART(상호 희망 상대 없음) 등",
                example = "NO_COUNTERPART") String reason,
        @Schema(description = "검사한 후보 수", example = "3") int candidatesChecked,
        @Schema(description = "만들어진 매칭 세션. matched=false면 null") MatchSessionResponse session
) {
    public static QuickMatchResponse notMatched(String reason, int candidatesChecked) {
        return new QuickMatchResponse(false, reason, candidatesChecked, null);
    }

    public static QuickMatchResponse matched(MatchSessionResponse session, int candidatesChecked) {
        return new QuickMatchResponse(true, null, candidatesChecked, session);
    }
}
