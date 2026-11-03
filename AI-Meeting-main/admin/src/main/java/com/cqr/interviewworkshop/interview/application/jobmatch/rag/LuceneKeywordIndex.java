package com.cqr.interviewworkshop.interview.application.jobmatch.rag;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 单实例本地索引。Lucene 的 BM25Similarity 实际计算词频、逆文档频率和长度归一化。 */
@Component
@Profile("!redis-rag")
@Lazy
public class LuceneKeywordIndex {
    private static final String DOC_ID = "documentId";
    private static final String CHUNK_ID = "chunkId";
    private static final String PENDING_DOC_ID = "pendingDocumentId";
    private static final String HASH = "contentHash";
    private static final String TEXT = "content";
    private final SmartChineseAnalyzer analyzer = new SmartChineseAnalyzer();
    private final Directory directory;

    public LuceneKeywordIndex(RagProperties properties) throws IOException {
        String configured = properties.getIndexPath().replace("${user.home}", System.getProperty("user.home"));
        directory = FSDirectory.open(Path.of(configured));
        try (IndexWriter writer = writer()) { writer.commit(); }
    }

    private IndexWriter writer() throws IOException {
        return new IndexWriter(directory, new IndexWriterConfig(analyzer));
    }

    public synchronized void replace(String documentId, List<JobKnowledgeDocument> chunks) {
        try (IndexWriter writer = writer()) {
            writer.deleteDocuments(new Term(DOC_ID, documentId));
            writer.deleteDocuments(new Term(PENDING_DOC_ID, documentId));
            for (JobKnowledgeDocument chunk : chunks) {
                org.apache.lucene.document.Document indexed = new org.apache.lucene.document.Document();
                indexed.add(new StringField(DOC_ID, chunk.getDocumentId(), Field.Store.YES));
                indexed.add(new StringField(CHUNK_ID, chunk.getChunkId(), Field.Store.YES));
                indexed.add(new StringField("chunkIndex", String.valueOf(chunk.getChunkIndex()), Field.Store.YES));
                indexed.add(new StringField(HASH, chunk.getContentHash(), Field.Store.YES));
                indexed.add(new StringField("source", safe(chunk.getSource()), Field.Store.YES));
                indexed.add(new StringField("tag", safe(chunk.getTag()), Field.Store.YES));
                indexed.add(new StringField("title", safe(chunk.getTitle()), Field.Store.YES));
                indexed.add(new TextField(TEXT, chunk.getContent(), Field.Store.YES));
                writer.addDocument(indexed);
            }
            writer.commit();
        } catch (IOException ex) {
            throw new IllegalStateException("Lucene 索引更新失败", ex);
        }
    }

    public synchronized Map<String, List<JobKnowledgeDocument>> snapshot() {
        Map<String, List<JobKnowledgeDocument>> result = new HashMap<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (ScoreDoc hit : searcher.search(new MatchAllDocsQuery(), Math.max(1, reader.numDocs())).scoreDocs) {
                org.apache.lucene.document.Document stored = searcher.storedFields().document(hit.doc);
                if (stored.get(DOC_ID) == null) continue; // 未完成写入的清理记录不是可检索知识
                JobKnowledgeDocument document = fromLucene(stored);
                result.computeIfAbsent(document.getDocumentId(), key -> new ArrayList<>()).add(document);
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Lucene 索引读取失败", ex);
        }
    }

    /** 向量写入前先持久化目标 ID；重试时可清理部分写入的新 Chunk。 */
    public synchronized void markPending(String documentId, List<String> targetIds) {
        java.util.Set<String> all = new java.util.LinkedHashSet<>(pendingSnapshot()
                .getOrDefault(documentId, List.of()));
        all.addAll(targetIds);
        try (IndexWriter writer = writer()) {
            writer.deleteDocuments(new Term(PENDING_DOC_ID, documentId));
            for (String chunkId : all) {
                org.apache.lucene.document.Document marker = new org.apache.lucene.document.Document();
                marker.add(new StringField(PENDING_DOC_ID, documentId, Field.Store.YES));
                marker.add(new StringField(CHUNK_ID, chunkId, Field.Store.YES));
                writer.addDocument(marker);
            }
            writer.commit();
        } catch (IOException ex) {
            throw new IllegalStateException("Lucene pending 记录写入失败", ex);
        }
    }

    public synchronized Map<String, List<String>> pendingSnapshot() {
        Map<String, List<String>> result = new HashMap<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (ScoreDoc hit : searcher.search(new MatchAllDocsQuery(), Math.max(1, reader.numDocs())).scoreDocs) {
                org.apache.lucene.document.Document stored = searcher.storedFields().document(hit.doc);
                String documentId = stored.get(PENDING_DOC_ID);
                if (documentId != null) {
                    result.computeIfAbsent(documentId, key -> new ArrayList<>()).add(stored.get(CHUNK_ID));
                }
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Lucene pending 记录读取失败", ex);
        }
    }

    public synchronized List<JobKnowledgeDocument> search(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) return List.of();

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            BooleanQuery.Builder terms = new BooleanQuery.Builder();
            try (TokenStream tokens = analyzer.tokenStream(TEXT, query)) {
                CharTermAttribute term = tokens.addAttribute(CharTermAttribute.class);
                tokens.reset();
                int count = 0;
                while (tokens.incrementToken() && count++ < 100) {
                    terms.add(new TermQuery(new Term(TEXT, term.toString())), BooleanClause.Occur.SHOULD);
                }
                tokens.end();
            }
            if (terms.build().clauses().isEmpty()) return List.of();
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            List<JobKnowledgeDocument> result = new ArrayList<>();
            for (ScoreDoc hit : searcher.search(terms.build(), limit).scoreDocs) {
                result.add(fromLucene(searcher.storedFields().document(hit.doc)));
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("BM25 检索失败", ex);
        }
    }

    private JobKnowledgeDocument fromLucene(org.apache.lucene.document.Document indexed) {
        JobKnowledgeDocument result = new JobKnowledgeDocument();
        result.setDocumentId(indexed.get(DOC_ID));
        result.setChunkId(indexed.get(CHUNK_ID));
        String ordinal = indexed.get("chunkIndex");
        if (ordinal != null && !ordinal.equals("null")) result.setChunkIndex(Integer.parseInt(ordinal));
        result.setContentHash(indexed.get(HASH));
        result.setContent(indexed.get(TEXT));
        result.setSource(indexed.get("source"));
        result.setTag(indexed.get("tag"));
        result.setTitle(indexed.get("title"));
        return result;
    }

    private String safe(String value) { return value == null ? "" : value; }

    @PreDestroy
    public void close() throws IOException {
        directory.close();
        analyzer.close();
    }
}
