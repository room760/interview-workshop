package com.cqr.interviewworkshop.interview.api.io.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewAnswerReqDTO {

    @NotBlank(message = "questionNumber cannot be blank")
    @Size(max = 32, message = "questionNumber length must be less than or equal to 32")
    private String questionNumber;

    @NotBlank(message = "answerContent cannot be blank")
    @Size(max = 5000, message = "answerContent length must be less than or equal to 5000")
    private String answerContent;

    private String sessionId;

    @Size(max = 64, message = "requestId length must be less than or equal to 64")
    private String requestId;

    @Size(max = 200, message = "jobTitle length must be less than or equal to 200")
    private String jobTitle;

    @Size(max = 3000, message = "jobRequirement length must be less than or equal to 3000")
    private String jobRequirement;

    @Size(max = 200, message = "companyName length must be less than or equal to 200")
    private String companyName;

    @Size(max = 253, message = "officialDomain length must be less than or equal to 253")
    private String officialDomain;

}
