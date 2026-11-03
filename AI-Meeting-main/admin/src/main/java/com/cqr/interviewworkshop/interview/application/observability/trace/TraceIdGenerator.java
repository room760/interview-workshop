package com.cqr.interviewworkshop.interview.application.observability.trace;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TraceIdGenerator {
    public String generate(){
        return UUID.randomUUID()
                .toString()
                .replace("-","");
    }
}
