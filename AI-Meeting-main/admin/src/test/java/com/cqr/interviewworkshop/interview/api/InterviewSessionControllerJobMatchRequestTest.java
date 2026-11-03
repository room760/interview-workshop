package com.cqr.interviewworkshop.interview.api;

import com.cqr.interviewworkshop.common.convention.context.UserContext;
import com.cqr.interviewworkshop.interview.api.io.req.InterviewAnswerReqDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewAnswerRespDTO;
import com.cqr.interviewworkshop.interview.flow.session.InterviewSessionFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewSessionControllerJobMatchRequestTest {

    @Test
    void shouldPassOptionalJobFieldsFromFormEndpointToFacade() {
        InterviewSessionFacade facade = mock(InterviewSessionFacade.class);
        InterviewSessionController controller = new InterviewSessionController(facade);
        when(facade.answerInterviewQuestion(eq("session-1"), any(InterviewAnswerReqDTO.class), eq(7L)))
                .thenReturn(InterviewAnswerRespDTO.init().success());

        controller.answerInterviewQuestion(
                "session-1",
                "1",
                "I use cache-aside.",
                "request-1",
                "Java Engineer",
                "Spring and Redis",
                "Example Inc.",
                "careers.example.com",
                new UserContext(7L, "tester")
        );

        ArgumentCaptor<InterviewAnswerReqDTO> captor = ArgumentCaptor.forClass(InterviewAnswerReqDTO.class);
        verify(facade).answerInterviewQuestion(eq("session-1"), captor.capture(), eq(7L));
        InterviewAnswerReqDTO request = captor.getValue();
        assertEquals("Java Engineer", request.getJobTitle());
        assertEquals("Spring and Redis", request.getJobRequirement());
        assertEquals("Example Inc.", request.getCompanyName());
        assertEquals("careers.example.com", request.getOfficialDomain());
    }
}
