package com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository.JobKnowledgeRepository;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@Profile("redis-rag")
@RequiredArgsConstructor
public class RedisVectorJobKnowledgeRepository implements JobKnowledgeRepository {
    private static final String KEY_PREFIX = "job:knowledge:";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_TAG = "tag";
    private static final String FIELD_VECTOR = "vector";

    private final EmbeddingService embeddingService;
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public void save(JobKnowledgeDocument document) throws IllegalAccessException {
        if(document == null || StrUtil.isBlank(document.getContent())){
            throw new IllegalAccessException("岗位知识内容不能为空");
        }
        List<Double> vector = embeddingService.embed(document.getContent());
        if(vector == null || vector.isEmpty()){
            throw new IllegalStateException("岗位知识向量化失败");
        }
        String key = KEY_PREFIX + UUID.fastUUID();
        Map<String,String> data = new HashMap<>();
        data.put(FIELD_CONTENT, StrUtil.blankToDefault(document.getContent(), ""));
        data.put(FIELD_SOURCE, StrUtil.blankToDefault(document.getSource(), ""));
        data.put(FIELD_TAG, StrUtil.blankToDefault(document.getTag(), ""));
        data.put(FIELD_VECTOR, JSON.toJSONString(vector));

        stringRedisTemplate
                .opsForHash()
                .putAll(key,data);
    }

    @Override
    public List<JobKnowledgeDocument> search(String query, int topK) {
        if(StrUtil.isBlank(query) || topK <= 0){
            return List.of();
        }
        try{
            List<Double> queryVector = embeddingService.embed(query);
            if(queryVector == null || queryVector.isEmpty()){
                return List.of();
            }
            List<ScoredDocument> candidates = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(KEY_PREFIX + "*")
                    .count(100)
                    .build();
            try(Cursor<String> cursor = stringRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    Map<Object, Object> data = stringRedisTemplate
                            .opsForHash()
                            .entries(key);
                    if (data == null || data.isEmpty()) {
                        continue;
                    }

                    Object contentValue = data.get(FIELD_CONTENT);
                    Object sourceValue = data.get(FIELD_SOURCE);
                    Object tagValue = data.get(FIELD_TAG);
                    Object vectorValue = data.get(FIELD_VECTOR);
                    if (contentValue == null || vectorValue == null) {
                        continue;
                    }

                    String content = String.valueOf(contentValue);
                    String vectorJson = String.valueOf(vectorValue);
                    List<Double> documentVector = JSON.parseArray(vectorJson, Double.class);
                    if (documentVector == null || documentVector.isEmpty()) {
                        continue;
                    }

                    // 3. 计算查询向量与知识向量的余弦相似度
                    double similarity = cosineSimilarity(queryVector, documentVector);
                    JobKnowledgeDocument document = new JobKnowledgeDocument();
                    document.setContent(content);
                    document.setSource(sourceValue == null ? null : String.valueOf(sourceValue));
                    document.setTag(tagValue == null ? null : String.valueOf(tagValue));
                    candidates.add(new ScoredDocument(document, similarity));
                }
                // 4. 按相似度从高到低排序
                return candidates.stream()
                        .sorted(Comparator.comparingDouble(ScoredDocument::similarity).reversed())
                        .limit(topK)
                        .map(ScoredDocument::document)
                        .toList();
            }
        } catch (IllegalAccessException ex) {
                throw new IllegalStateException("岗位知识检索向量化失败", ex);
        } catch (Exception ex) {
                throw new IllegalStateException("岗位知识检索失败", ex);
        }
    }
    /**
     * 计算两个向量的余弦相似度。
     */
    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0D;
        }
        double dotProduct = 0D;
        double normA = 0D;
        double normB = 0D;
        for (int i = 0; i < a.size(); i++) {
            double valueA = a.get(i);
            double valueB = b.get(i);
            dotProduct += valueA * valueB;
            normA += valueA * valueA;
            normB += valueB * valueB;
        }
        if (normA == 0D || normB == 0D) {
            return 0D;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredDocument(JobKnowledgeDocument document, double similarity) { }
}
