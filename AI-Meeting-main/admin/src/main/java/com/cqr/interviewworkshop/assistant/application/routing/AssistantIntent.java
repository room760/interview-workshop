package com.cqr.interviewworkshop.assistant.application.routing;

public enum AssistantIntent {
    /**
     * 用户正在回答当前面试题，需要进入原有面试链路。
     */
    INTERVIEW_ANSWER,

    /**
     * 用户想了解目标公司、目标岗位及备战知识。
     */
    JOB_PREPARATION,

    /**
     * 用户意图不明确，需要进一步询问。
     */
    CLARIFY
}
