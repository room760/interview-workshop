package com.cqr.interviewworkshop.interview.application.observability.exception;

import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardException;
import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardStage;
import org.springframework.stereotype.Component;

@Component
public class AiExceptionResolver {
    public String resolve(Exception e){
        if(e instanceof InterviewAiGuardException guardException){
            return guardException
                    .getErrorCode()
                    .name();
        }
        Throwable cause = e.getCause();
        if(cause instanceof InterviewAiGuardException guardException){
            return guardException
                    .getErrorCode()
                    .name();
        }
        return "UNKNOWN_ERROR";
    }
}
