package com.cqr.interviewworkshop.interview.application.observability.service;

import com.cqr.interviewworkshop.interview.dao.entity.AiCallRecordDO;

public interface AiCallRecordService {
    void save(AiCallRecordDO record);
}
