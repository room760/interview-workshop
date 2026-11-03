
package com.cqr.interviewworkshop.interview.application.jobmatch.rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@Profile("!redis-rag")
public class PgVectorStoreConfig {

    /**
     * 原有业务数据库：MySQL。
     * 显式声明为主数据源，供 MyBatis 等业务组件使用。
     */
    @Bean(name = "mysqlDataSource")
    @Primary
    public DataSource mysqlDataSource(
            @Value("${spring.datasource.driver-class-name}")
            String driverClassName,
            @Value("${spring.datasource.url}")
            String jdbcUrl,
            @Value("${spring.datasource.username}")
            String username,
            @Value("${spring.datasource.password}")
            String password) {

        return DataSourceBuilder.create()
                .driverClassName(driverClassName)
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }

    /**
     * 独立的 PostgreSQL 数据源，仅供 pgvector 使用。
     */
    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource(
            @Value("${interview-workshop-agent.rag.pgvector.jdbc-url}")
            String jdbcUrl,
            @Value("${interview-workshop-agent.rag.pgvector.username}")
            String username,
            @Value("${interview-workshop-agent.rag.pgvector.password}")
            String password) {

        return DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }

    /**
     * 专门访问 PostgreSQL 的 JdbcTemplate。
     */
    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(
            @Qualifier("pgVectorDataSource")
            DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }

    /**
     * Spring AI 的 pgvector 向量存储。
     */
    @Bean
    public PgVectorStore jobKnowledgePgVectorStore(
            @Qualifier("pgVectorJdbcTemplate")
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            @Value("${interview-workshop-agent.rag.pgvector.dimensions:1536}")
            int dimensions,
            @Value("${interview-workshop-agent.rag.pgvector.table-name:job_knowledge_vector}")
            String tableName) {

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(tableName)
                .dimensions(dimensions)
                .initializeSchema(true)
                .build();
    }
}
