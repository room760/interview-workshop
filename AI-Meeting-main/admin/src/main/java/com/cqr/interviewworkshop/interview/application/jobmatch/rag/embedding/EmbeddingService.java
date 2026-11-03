package com.cqr.interviewworkshop.interview.application.jobmatch.rag.embedding;

import java.util.List;

public interface EmbeddingService {
    List<Double> embed(String text) throws IllegalAccessException;
}
