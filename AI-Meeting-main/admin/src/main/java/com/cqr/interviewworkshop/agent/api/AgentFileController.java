package com.cqr.interviewworkshop.agent.api;

import com.cqr.interviewworkshop.agent.api.io.resp.AgentFileUploadRespDTO;
import com.cqr.interviewworkshop.agent.service.AgentFileAssetService;
import com.cqr.interviewworkshop.common.convention.annotation.CurrentUser;
import com.cqr.interviewworkshop.common.convention.result.Result;
import com.cqr.interviewworkshop.common.convention.result.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interview-workshop/v1/agents/files")
public class AgentFileController {

    private final AgentFileAssetService agentFileAssetService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AgentFileUploadRespDTO> upload(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestPart("file") MultipartFile file,
            @CurrentUser String username) {
        return Results.success(agentFileAssetService.uploadAndPersist(sessionId, bizType, username, file));
    }
}
