package com.cqr.interviewworkshop.interview.application.prompt.management;

import com.cqr.interviewworkshop.interview.dao.entity.PromptVersionDO;

import java.util.List;

public interface PromptManagementService {
    List<?> listTemplates();
    List<PromptVersionDO> listVersions(Long templateId);
    Long createVersion(Long templateId,String version,String content);
    void activateVersion(Long templateId,Long versionId);
}
