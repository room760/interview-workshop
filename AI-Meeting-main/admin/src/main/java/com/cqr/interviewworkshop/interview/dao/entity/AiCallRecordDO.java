package com.cqr.interviewworkshop.interview.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_call_record")
public class AiCallRecordDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String businessType;

    private String agentName;
    private String modelName;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long latency;

    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String status;
    private String errorCode;
    private String errorMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
