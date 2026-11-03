package com.cqr.interviewworkshop.interview.application.jobmatch.rag.embedding.impl;

import com.cqr.interviewworkshop.interview.application.jobmatch.rag.embedding.EmbeddingService;
import com.cqr.interviewworkshop.interview.shared.InterviewAiInvoker;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiEmbeddingService implements EmbeddingService {
    private final EmbeddingModel embeddingModel;
    @Override
    public List<Double> embed(String text) throws IllegalAccessException {
        if(text == null || text.isBlank()){
            throw new IllegalAccessException("待向量化文本不能为空");
        }
        float[] vector = embeddingModel.embed(text.trim());
        if(vector == null || vector.length == 0){
            throw new IllegalAccessException("向量模型返回了空向量");
        }
        List<Double> result = new ArrayList<>(vector.length);
        for(float value : vector){
            if(!Float.isFinite(value)){
                throw new IllegalAccessException("向量模型返回了无效数值");
            }
            result.add((double) value);
        }
        return result;
    }
}
