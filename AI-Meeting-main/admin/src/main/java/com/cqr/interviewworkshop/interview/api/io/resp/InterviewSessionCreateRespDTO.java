package com.cqr.interviewworkshop.interview.api.io.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewSessionCreateRespDTO {

    private String sessionId;

    private String status;
}
