package com.cqr.interviewworkshop.interview.application.prompt.core.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cqr.interviewworkshop.interview.application.prompt.core.PromptService;
import com.cqr.interviewworkshop.interview.dao.entity.PromptTemplateDO;
import com.cqr.interviewworkshop.interview.dao.entity.PromptVersionDO;
import com.cqr.interviewworkshop.interview.dao.mapper.PromptTemplateMapper;
import com.cqr.interviewworkshop.interview.dao.mapper.PromptVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptServiceImpl implements PromptService {
    //Prompt参数格式
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile(
                    "\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}"
            );
    private final PromptTemplateMapper promptTemplateMapper;
    private final PromptVersionMapper promptVersionMapper;

    @Override
    public String getPrompt(String promptCode) {
        if (StrUtil.isBlank(promptCode)) {
            throw new IllegalArgumentException("Prompt编码不能为空");
        }
        String normalizedPromptCode = promptCode.trim();
        // 1. 查询启用状态的 Prompt 模板
        PromptTemplateDO template = promptTemplateMapper.selectOne(
                Wrappers.lambdaQuery(PromptTemplateDO.class)
                        .eq(PromptTemplateDO::getPromptCode,normalizedPromptCode)
                        .eq(PromptTemplateDO::getStatus,1)
        );
        if(template == null){
            throw new RuntimeException("Prompt不存在:" + normalizedPromptCode);
        }
        // 2. 查询当前生效版本
        List<PromptVersionDO> versions = promptVersionMapper.selectList(
                Wrappers.lambdaQuery(PromptVersionDO.class)
                        .eq(PromptVersionDO::getTemplateId,template.getId())
                        .eq(PromptVersionDO::getStatus,1)
                        .orderByDesc(PromptVersionDO::getId)
                        .last("LIMIT 1")
        );
        if(versions == null || versions.isEmpty()){
            throw new RuntimeException("Prompt版本不存在:" + normalizedPromptCode);
        }
        PromptVersionDO version = versions.get(0);
        if (StrUtil.isBlank(version.getContent())) {
            throw new IllegalStateException(
                    "Prompt内容为空: "
                            + normalizedPromptCode
                            + ", version="
                            + version.getVersion());}
        return version.getContent();
    }

    @Override
    public String renderPrompt(String promptCode, Map<String, Object> parameters) {
        String template = getPrompt(promptCode);
        if (StrUtil.isBlank(template)) {
            return "";
        }
        Map<String, Object> safeParameters = parameters == null ? Collections.emptyMap() : parameters;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!safeParameters.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Prompt参数缺失: "
                                + key
                                + ", promptCode="
                                + promptCode
                );
            }
            Object value = safeParameters.get(key);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Prompt参数不能为null: "
                                + key
                                + ", promptCode="
                                + promptCode
                );
            }
            matcher.appendReplacement(
                    rendered,
                    Matcher.quoteReplacement(
                            String.valueOf(value)
                    )
            );
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
