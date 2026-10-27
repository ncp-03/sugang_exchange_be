package com.ncp03.coursechange.common.error;

import org.springframework.http.HttpStatus;

/** API 에러 코드. 프론트는 code 값으로 분기하고, message는 화면에 그대로 보여 줘도 된다. */
public enum ErrorCode {
    MISSING_STUDENT_ID(HttpStatus.UNAUTHORIZED, "X-Student-Id 헤더가 필요합니다."),
    INVALID_STUDENT_ID(HttpStatus.BAD_REQUEST, "X-Student-Id 헤더는 1 이상의 숫자여야 합니다."),
    MISSING_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다. Authorization: Bearer <토큰> 헤더를 보내 주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 올바르지 않습니다. 다시 로그인해 주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "학번 또는 비밀번호가 올바르지 않습니다."),
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 학생입니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 강의(분반)입니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),

    // ---------- 찜(하트) ----------
    WISHLIST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 찜한 분반입니다."),
    WISHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "찜 목록에 없는 분반입니다."),
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "이미 수강 중인 분반입니다."),

    // ---------- 교환 요청 ----------
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 수강 내역입니다."),
    NOT_MY_ENROLLMENT(HttpStatus.FORBIDDEN, "내 수강 내역이 아닙니다."),
    REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 수강 내역으로 진행 중인 교환 요청이 이미 있습니다."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 교환 요청입니다."),
    NOT_MY_REQUEST(HttpStatus.FORBIDDEN, "내 교환 요청이 아닙니다."),
    REQUEST_NOT_MODIFIABLE(HttpStatus.CONFLICT, "지금 상태에서는 수정하거나 취소할 수 없습니다."),
    PREFERENCES_REQUIRED(HttpStatus.BAD_REQUEST, "희망 분반을 1개 이상 보내야 합니다."),
    TOO_MANY_PREFERENCES(HttpStatus.BAD_REQUEST, "희망 분반은 최대 10개까지 보낼 수 있습니다."),
    DUPLICATE_PREFERENCE(HttpStatus.BAD_REQUEST, "희망 분반이 중복됐습니다."),
    PREFERENCE_SAME_AS_OFFERED(HttpStatus.BAD_REQUEST, "내놓는 분반을 희망 분반으로 넣을 수 없습니다."),
    STUDENT_BLOCKED(HttpStatus.FORBIDDEN, "패널티로 교환 이용이 제한된 상태입니다."),

    // ---------- 교환 가능성 검증 ----------
    CREDIT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "교환하면 신청 가능 최대 학점을 넘습니다."),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "교환하면 시간표가 겹칩니다."),
    SAME_COURSE_ALREADY_HELD(HttpStatus.CONFLICT, "같은 과목의 다른 분반을 이미 수강 중입니다."),

    // ---------- 매칭 세션 ----------
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매칭 세션입니다."),
    NOT_MY_SESSION(HttpStatus.FORBIDDEN, "내가 참여한 매칭 세션이 아닙니다."),
    SESSION_NOT_PENDING(HttpStatus.CONFLICT, "이미 끝난 매칭 세션입니다."),
    ALREADY_RESPONDED(HttpStatus.CONFLICT, "이미 응답한 매칭입니다."),
    SESSION_EXPIRED(HttpStatus.CONFLICT, "응답 제한 시간이 지났습니다."),
    WAKE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "깨우기 횟수를 모두 사용했습니다."),

    // ---------- 동시성 ----------
    EXCHANGE_CONFLICT(HttpStatus.CONFLICT, "다른 요청이 먼저 처리됐습니다. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() { return status; }
    public String getDefaultMessage() { return defaultMessage; }
}
