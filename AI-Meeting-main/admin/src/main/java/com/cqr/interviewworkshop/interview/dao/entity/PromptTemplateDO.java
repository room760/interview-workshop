package com.cqr.interviewworkshop.interview.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_template")
public class PromptTemplateDO {
    private Long id;

    private String promptCode;
    private String promptName;

    private String agentType;
    private String description;
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
