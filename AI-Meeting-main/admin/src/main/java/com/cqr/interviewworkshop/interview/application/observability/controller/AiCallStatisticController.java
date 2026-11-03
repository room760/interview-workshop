package com.cqr.interviewworkshop.interview.application.observability.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cqr.interviewworkshop.common.convention.result.Result;
import com.cqr.interviewworkshop.common.convention.result.Results;
import com.cqr.interviewworkshop.interview.application.observability.dto.AiCallStatisticDTO;
import com.cqr.interviewworkshop.interview.application.observability.service.AiCallStatisticService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/interview-workshop/v1/interview/ai")
@RequiredArgsConstructor
public class AiCallStatisticController {
    private final AiCallStatisticService aiCallStatisticService;

    @SaCheckRole("admin")
    @GetMapping("/statistic")
    public Result<AiCallStatisticDTO> statistic(){
        return Results.success(aiCallStatisticService.statistic());
    }
}
