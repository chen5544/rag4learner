package com.example.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${app.ai.chat.api-key}")
    private String chatApiKey;

    @Value("${app.ai.chat.base-url}")
    private String chatBaseUrl;

    @Value("${app.ai.chat.model-name}")
    private String chatModelName;

    @Value("${app.ai.chat.temperature}")
    private double temperature;

    @Value("${app.ai.chat.max-tokens}")
    private int maxTokens;

    @Value("${app.ai.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${app.ai.embedding.base-url}")
    private String embeddingBaseUrl;

    @Value("${app.ai.embedding.model-name}")
    private String embeddingModelName;

    @Value("${app.ai.embedding.dimension}")
    private int dimension;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(chatBaseUrl)
                .apiKey(chatApiKey)
                .modelName(chatModelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // 从 jdbc:postgresql://host:port/db 中解析连接参数
        String url = dbUrl.replace("jdbc:postgresql://", "");
        String host = url.split("[:/]")[0];
        String port = "5432";
        String database = url.contains(":") ? url.substring(url.indexOf(":") + 1) : url;
        if (database.contains("/")) {
            port = database.substring(0, database.indexOf("/"));
            database = database.substring(database.indexOf("/") + 1);
        }
        if (database.contains("?")) {
            database = database.substring(0, database.indexOf("?"));
        }

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(Integer.parseInt(port))
                .database(database)
                .user(dbUser)
                .password(dbPassword)
                .table("document_chunks")
                .dimension(dimension)
                .createTable(true)
                .dropTableFirst(false)
                .build();
    }
}
