package com.cqr.interviewworkshop.interview.application.prompt.management.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cqr.interviewworkshop.interview.application.prompt.management.PromptManagementService;
import com.cqr.interviewworkshop.interview.dao.entity.PromptTemplateDO;
import com.cqr.interviewworkshop.interview.dao.entity.PromptVersionDO;
import com.cqr.interviewworkshop.interview.dao.mapper.PromptTemplateMapper;
import com.cqr.interviewworkshop.interview.dao.mapper.PromptVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptManagementServiceImpl implements PromptManagementService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PromptVersionMapper promptVersionMapper;

    @Override
    public List<?> listTemplates() {
        return promptTemplateMapper.selectList(
                Wrappers.lambdaQuery(PromptTemplateDO.class)
        );
    }

    @Override
    public List<PromptVersionDO> listVersions(Long templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("templateId不能为空");
        }
        return promptVersionMapper.selectList(
                Wrappers.lambdaQuery(PromptVersionDO.class)
                        .eq(PromptVersionDO::getTemplateId, templateId)
                        .orderByDesc(PromptVersionDO::getId)
        );
    }

    @Override
    public Long createVersion(Long templateId, String version, String content) {
        if (templateId == null) {
            throw new IllegalArgumentException("templateId不能为空");
        }
        if (StrUtil.isBlank(version)) {
            throw new IllegalArgumentException("Prompt版本号不能为空");
        }
        if (StrUtil.isBlank(content)) {
            throw new IllegalArgumentException("Prompt内容不能为空");
        }
        // 1. 检查模板是否存在
        PromptTemplateDO template = promptTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalStateException("Prompt模板不存在: " + templateId);
        }
        // 2. 防止同一个模板出现重复版本号
        Long count = promptVersionMapper.selectCount(
                        Wrappers.lambdaQuery(PromptVersionDO.class)
                                .eq(PromptVersionDO::getTemplateId, templateId)
                                .eq(PromptVersionDO::getVersion, version.trim())
                );

        if (count != null && count > 0) {
            throw new IllegalStateException("Prompt版本已经存在: " + version);
        }
        // 3. 创建新版本
        PromptVersionDO promptVersion = new PromptVersionDO();
        promptVersion.setTemplateId(templateId);
        promptVersion.setVersion(version.trim());
        promptVersion.setContent(content);
        // 新版本默认不生效
        promptVersion.setStatus(0);
        LocalDateTime now = LocalDateTime.now();
        promptVersion.setCreateTime(now);
        promptVersion.setUpdateTime(now);
        promptVersionMapper.insert(promptVersion);
        log.info(
                "Prompt version created, templateId={}, versionId={}, version={}",
                templateId,
                promptVersion.getId(),
                promptVersion.getVersion()
        );
        return promptVersion.getId();
    }

    @Override
    public void activateVersion(Long templateId, Long versionId) {
        if (templateId == null) {
            throw new IllegalArgumentException("templateId不能为空");
        }
        if (versionId == null) {
            throw new IllegalArgumentException("versionId不能为空");
        }
        // 1. 检查版本是否属于当前模板
        PromptVersionDO version = promptVersionMapper.selectOne(
                        Wrappers.lambdaQuery(PromptVersionDO.class)
                                .eq(PromptVersionDO::getId, versionId)
                                .eq(PromptVersionDO::getTemplateId, templateId)
                );
        if (version == null) {
            throw new IllegalStateException("Prompt版本不存在或不属于当前模板");
        }
        // 2. 关闭当前模板所有已生效版本
        promptVersionMapper.update(
                null,
                Wrappers.lambdaUpdate(PromptVersionDO.class)
                        .eq(PromptVersionDO::getTemplateId, templateId)
                        .set(PromptVersionDO::getStatus, 0)
        );
        // 3. 激活目标版本
        PromptVersionDO update = new PromptVersionDO();
        update.setId(versionId);
        update.setStatus(1);
        update.setUpdateTime(LocalDateTime.now());
        int affected = promptVersionMapper.updateById(update);
        if (affected != 1) {
            throw new IllegalStateException("Prompt版本激活失败");
        }
        log.info(
                "Prompt version activated, templateId={}, versionId={}, version={}",
                templateId,
                versionId,
                version.getVersion()
        );
    }
}
