package com.cqr.interviewworkshop.interview.application.observability.service.impl;

import com.cqr.interviewworkshop.interview.application.observability.dto.AiCallStatisticDTO;
import com.cqr.interviewworkshop.interview.dao.mapper.AiCallRecordMapper;
import com.cqr.interviewworkshop.interview.application.observability.service.AiCallStatisticService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiCallStatisticServiceImpl implements AiCallStatisticService {
    private final AiCallRecordMapper mapper;
    @Override
    public AiCallStatisticDTO statistic() {
        AiCallStatisticDTO dto = new AiCallStatisticDTO();
        dto.setTotalCalls(mapper.selectCount(null));
        dto.setSuccessCalls(mapper.countSuccess());
        dto.setFailedCalls(mapper.countFailed());
        dto.setTotalTokens(mapper.sumTokens());
        dto.setAvgLatency(mapper.avgLatency());
        dto.setAgentStatistics(mapper.statisticByAgent());
        return dto;
    }
}
