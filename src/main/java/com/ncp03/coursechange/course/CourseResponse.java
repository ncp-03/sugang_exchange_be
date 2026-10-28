package com.ncp03.coursechange.course;

import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.CourseType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "강의(개설 분반) 정보")
public record CourseResponse(
        @Schema(description = "분반 ID. 수강·교환·찜은 모두 이 값을 쓴다", example = "9") Long offeringId,
        @Schema(description = "과목 ID", example = "5") Long courseId,
        @Schema(description = "학수번호", example = "CSE3050") String courseCode,
        @Schema(description = "과목명", example = "프로그래밍언어론") String courseName,
        @Schema(description = "분반", example = "01") String sectionNo,
        @Schema(description = "교수명", example = "서교수") String professorName,
        @Schema(description = "개설 학과", example = "컴퓨터공학과") String department,
        @Schema(description = "대상학년", example = "3") int targetYear,
        @Schema(description = "이수 구분 코드", example = "MAJOR_ELECTIVE") CourseType courseType,
        @Schema(description = "이수 구분 한글", example = "전공선택") String courseTypeLabel,
        @Schema(description = "학점", example = "3") int credits,
        @Schema(description = "정원", example = "30") int capacity,
        @Schema(description = "현재 수강 인원", example = "2") long enrolledCount,
        @Schema(description = "정원이 다 찼는지 (화면의 '마감' 표시)", example = "false") boolean full,
        @Schema(description = "강의실", example = "공학관 501") String classroom,
        @Schema(description = "수업 시간 목록") List<ScheduleResponse> schedules,
        @Schema(description = "수업 시간 한 줄 표시", example = "화목 15:00-16:15") String scheduleText,
        @Schema(description = "교환 현황") ExchangeStatsResponse exchange
) {
    public static CourseResponse of(CourseOffering o, List<ScheduleResponse> schedules,
                                    long enrolledCount, ExchangeStatsResponse exchange) {
        var c = o.getCourse();
        return new CourseResponse(
                o.getId(), c.getId(), c.getCourseCode(), c.getCourseName(), o.getSectionNo(),
                o.getProfessorName(), c.getDepartment(), c.getTargetYear(),
                c.getCourseType(), c.getCourseType().getLabel(),
                o.getCredits(), o.getCapacity(), enrolledCount, enrolledCount >= o.getCapacity(),
                o.getClassroom(), schedules, ScheduleFormatter.format(schedules), exchange);
    }
}
