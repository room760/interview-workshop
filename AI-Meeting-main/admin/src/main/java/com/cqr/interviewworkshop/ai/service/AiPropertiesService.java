package com.cqr.interviewworkshop.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
import com.cqr.interviewworkshop.ai.api.io.req.AiPropertiesCreateReqDTO;
import com.cqr.interviewworkshop.ai.api.io.req.AiPropertiesPageReqDTO;
import com.cqr.interviewworkshop.ai.api.io.req.AiPropertiesUpdateReqDTO;
import com.cqr.interviewworkshop.ai.api.io.resp.AiPropertiesRespDTO;

import com.cqr.interviewworkshop.ai.api.io.resp.AiModelOptionRespDTO;

import java.util.List;

public interface AiPropertiesService extends IService<AiPropertiesDO> {

    void createAiProperties(AiPropertiesCreateReqDTO requestParam);

    void updateAiProperties(AiPropertiesUpdateReqDTO requestParam);

    void deleteAiProperties(Long id);

    AiPropertiesRespDTO getAiPropertiesById(Long id);

    IPage<AiPropertiesRespDTO> pageAiProperties(AiPropertiesPageReqDTO requestParam);

    List<AiPropertiesRespDTO> listEnabledAiProperties();

    void toggleAiPropertiesStatus(Long id, Integer isEnabled);

    List<AiPropertiesRespDTO> getAllEnabledAiProperties();

    AiPropertiesDO getEnabledByAiType(String aiType);

    AiPropertiesDO getDefaultDoubaoConfig();

    /**
     * 获取所有可用AI模型（用于前端下拉列表）
     */
    List<AiModelOptionRespDTO> getAvailableAiModels();
}

