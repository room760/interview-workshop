package com.cqr.interviewworkshop.interview.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_version")
public class PromptVersionDO {
    private Long id;

    private Long templateId;
    private String version;
    private String content;
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
