package com.cqr.interviewworkshop.interview.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqr.interviewworkshop.interview.application.observability.dto.AgentStatisticDTO;
import com.cqr.interviewworkshop.interview.dao.entity.AiCallRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiCallRecordMapper extends BaseMapper<AiCallRecordDO> {
    Long countSuccess();
    Long countFailed();
    Long sumTokens();
    Long avgLatency();
    List<AgentStatisticDTO> statisticByAgent();
}
