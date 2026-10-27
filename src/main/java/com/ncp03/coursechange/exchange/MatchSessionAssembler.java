package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.course.CourseAssembler;
import com.ncp03.coursechange.course.CourseResponse;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.MatchParticipant;
import com.ncp03.coursechange.domain.MatchParticipantRepository;
import com.ncp03.coursechange.domain.MatchSession;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 매칭 세션을 응답 DTO로 바꾼다. 분반 정보는 CourseAssembler를 재사용해 한 번에 모아 온다. */
@Component
public class MatchSessionAssembler {

    private final MatchParticipantRepository participantRepository;
    private final CourseAssembler courseAssembler;

    public MatchSessionAssembler(MatchParticipantRepository participantRepository, CourseAssembler courseAssembler) {
        this.participantRepository = participantRepository;
        this.courseAssembler = courseAssembler;
    }

    public MatchSessionResponse toResponse(MatchSession session, Long viewerStudentId, LocalDateTime now) {
        List<MatchParticipant> participants = participantRepository.findBySessionIdWithAll(session.getId());
        return toResponse(session, participants, viewerStudentId, now);
    }

    public MatchSessionResponse toResponse(MatchSession session, List<MatchParticipant> participants,
                                           Long viewerStudentId, LocalDateTime now) {
        // 두 참여자의 주는·받는 분반을 한 번에 조회 (분반 4건이지만 보통 중복돼 2건)
        List<CourseOffering> offerings = new ArrayList<>();
        for (MatchParticipant p : participants) {
            offerings.add(p.getGiveOffering());
            offerings.add(p.getReceiveOffering());
        }
        List<CourseOffering> distinct = offerings.stream()
                .collect(java.util.stream.Collectors.toMap(CourseOffering::getId, o -> o, (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();
        List<CourseResponse> responses = courseAssembler.toResponses(distinct);

        Map<Long, CourseResponse> byOfferingId = new HashMap<>();
        for (int i = 0; i < distinct.size(); i++) {
            byOfferingId.put(distinct.get(i).getId(), responses.get(i));
        }

        List<MatchParticipantResponse> views = participants.stream()
                .map(p -> new MatchParticipantResponse(
                        p.getStudent().getId().equals(viewerStudentId),
                        p.getStudent().getNickname(),
                        p.getResponseStatus().name(),
                        byOfferingId.get(p.getGiveOffering().getId()),
                        byOfferingId.get(p.getReceiveOffering().getId()),
                        p.getWakeSentCount()))
                .toList();

        long remaining = Math.max(0, Duration.between(now, session.getExpiresAt()).toSeconds());
        return new MatchSessionResponse(session.getId(), session.getStatus().name(), session.getOpenedAt(),
                session.getExpiresAt(), remaining, session.getFirstAcceptedAt(), views);
    }
}
