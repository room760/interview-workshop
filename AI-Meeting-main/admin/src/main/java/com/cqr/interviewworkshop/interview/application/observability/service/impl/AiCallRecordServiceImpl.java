package com.cqr.interviewworkshop.interview.application.observability.service.impl;

import com.cqr.interviewworkshop.interview.dao.entity.AiCallRecordDO;
import com.cqr.interviewworkshop.interview.dao.mapper.AiCallRecordMapper;
import com.cqr.interviewworkshop.interview.application.observability.service.AiCallRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiCallRecordServiceImpl implements AiCallRecordService {
    private final AiCallRecordMapper aiCallRecordMapper;

    @Override
    public void save(AiCallRecordDO record) {
        aiCallRecordMapper.insert(record);
    }
}
