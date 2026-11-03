package com.cqr.interviewworkshop.ai.api.io.req;

import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * AI配置分页查询请求DTO
 */
@Data
public class AiPropertiesPageReqDTO extends Page<AiPropertiesDO> {
    
    /**
     * AI名称（模糊查询）
     */
    private String aiName;
    
    /**
     * AI类型
     */
    private String aiType;
    
    /**
     * 是否启用 0：禁用 1：启用
     */
    private Integer isEnabled;
}
