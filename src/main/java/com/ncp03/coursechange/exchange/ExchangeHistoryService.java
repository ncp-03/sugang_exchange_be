package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.course.CourseAssembler;
import com.ncp03.coursechange.course.CourseResponse;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.MatchParticipant;
import com.ncp03.coursechange.domain.MatchParticipantRepository;
import com.ncp03.coursechange.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 끝난 교환을 화면의 '교환 기록' 표로 만들어 준다. */
@Service
@Transactional(readOnly = true)
public class ExchangeHistoryService {

    private final MatchParticipantRepository participantRepository;
    private final StudentService studentService;
    private final CourseAssembler courseAssembler;

    public ExchangeHistoryService(MatchParticipantRepository participantRepository,
                                  StudentService studentService,
                                  CourseAssembler courseAssembler) {
        this.participantRepository = participantRepository;
        this.studentService = studentService;
        this.courseAssembler = courseAssembler;
    }

    public ExchangeHistoryResponse myHistory(Long studentId) {
        studentService.getStudent(studentId);
        List<MatchParticipant> participants = participantRepository.findCompletedByStudentId(studentId);
        if (participants.isEmpty()) {
            return new ExchangeHistoryResponse(0, List.of());
        }

        // 분반 정보를 한 번에 모아 온다 (N+1 방지).
        Map<Long, CourseOffering> offerings = new LinkedHashMap<>();
        for (MatchParticipant p : participants) {
            offerings.putIfAbsent(p.getGiveOffering().getId(), p.getGiveOffering());
            offerings.putIfAbsent(p.getReceiveOffering().getId(), p.getReceiveOffering());
        }
        List<CourseOffering> list = List.copyOf(offerings.values());
        List<CourseResponse> responses = courseAssembler.toResponses(list);
        Map<Long, CourseResponse> byOfferingId = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            byOfferingId.put(list.get(i).getId(), responses.get(i));
        }

        List<ExchangeHistoryResponse.Item> items = new ArrayList<>();
        for (MatchParticipant p : participants) {
            items.add(new ExchangeHistoryResponse.Item(
                    p.getSession().getId(),
                    p.getSession().getFinishedAt(),
                    partnerNickname(p),
                    byOfferingId.get(p.getGiveOffering().getId()),
                    byOfferingId.get(p.getReceiveOffering().getId())));
        }
        return new ExchangeHistoryResponse(items.size(), items);
    }

    /** 학번은 노출하지 않고 상대의 익명 닉네임만 보여 준다. */
    private String partnerNickname(MatchParticipant me) {
        return participantRepository.findBySessionIdWithAll(me.getSession().getId()).stream()
                .filter(p -> !p.getStudent().getId().equals(me.getStudent().getId()))
                .map(p -> p.getStudent().getNickname())
                .findFirst()
                .orElse("알 수 없음");
    }
}
