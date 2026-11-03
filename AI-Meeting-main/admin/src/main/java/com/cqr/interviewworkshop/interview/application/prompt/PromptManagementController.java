package com.cqr.interviewworkshop.interview.application.prompt;

import com.cqr.interviewworkshop.interview.application.prompt.management.PromptManagementService;
import com.cqr.interviewworkshop.interview.dao.entity.PromptVersionDO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompt")
@RequiredArgsConstructor
public class PromptManagementController {

    private final PromptManagementService promptManagementService;

    /**
     * 查询所有 Prompt 模板。
     */
    @GetMapping("/templates")
    public List<?> listTemplates() {
        return promptManagementService.listTemplates();
    }

    /**
     * 查询指定模板的所有版本。
     */
    @GetMapping("/templates/{templateId}/versions")
    public List<PromptVersionDO> listVersions(@PathVariable Long templateId) {
        return promptManagementService.listVersions(templateId);
    }

    /**
     * 创建 Prompt 版本。
     */
    @PostMapping("/templates/{templateId}/versions")
    public Long createVersion(
            @PathVariable Long templateId,
            @RequestParam String version,
            @RequestBody String content) {
        return promptManagementService.createVersion(templateId,version,content);
    }

    /**
     * 激活 Prompt 版本。
     */
    @PostMapping(
            "/templates/{templateId}/versions/{versionId}/activate"
    )
    public void activateVersion(
            @PathVariable Long templateId,
            @PathVariable Long versionId) {
        promptManagementService.activateVersion(templateId, versionId);
    }
}