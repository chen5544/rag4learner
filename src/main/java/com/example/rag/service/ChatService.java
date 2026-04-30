package com.example.rag.service;

import com.example.rag.model.dto.ChatRequest;
import com.example.rag.model.dto.ChatResponse;
import com.example.rag.model.entity.Conversation;
import com.example.rag.model.entity.Message;
import com.example.rag.repository.ConversationRepository;
import com.example.rag.repository.MessageRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    @Value("${app.ai.rag.top-k}")
    private int topK;

    @Value("${app.ai.rag.similarity-threshold}")
    private double similarityThreshold;

    @Transactional
    public ChatResponse chat(ChatRequest request) {
        // 1. 获取或创建会话
        Conversation conversation = getOrCreateConversation(request.getConversationId());

        // 2. 保存用户消息
        messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .role("USER")
                .content(request.getMessage())
                .build());

        // 3. 首条消息自动命名
        if ("新对话".equals(conversation.getTitle())) {
            String title = request.getMessage().length() > 40
                    ? request.getMessage().substring(0, 40) + "..."
                    : request.getMessage();
            conversation.setTitle(title);
            conversationRepository.save(conversation);
        }

        // 4. 检索相关知识库内容
        String context = retrieveContext(request.getMessage());
        List<String> sources = retrieveSources(request.getMessage());

        // 5. 获取对话历史 + 构建 prompt
        List<Message> history = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        List<ChatMessage> chatMessages = buildChatMessages(
                history, context, request.getMessage());

        // 6. 调用 LLM
        String aiContent;
        try {
            Response<AiMessage> aiResponse = chatModel.generate(chatMessages);
            aiContent = aiResponse.content().text();
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            aiContent = "抱歉，AI 服务暂时不可用，请稍后重试。";
        }

        // 7. 保存 AI 回复
        messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .role("ASSISTANT")
                .content(aiContent)
                .build());

        return ChatResponse.builder()
                .conversationId(conversation.getId())
                .message(aiContent)
                .sources(sources)
                .build();
    }

    public List<Conversation> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<Message> getMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
        log.info("会话已删除: id={}", conversationId);
    }

    // ========== 私有方法 ==========

    private Conversation getOrCreateConversation(Long conversationId) {
        return Optional.ofNullable(conversationId)
                .flatMap(conversationRepository::findById)
                .orElseGet(() -> conversationRepository.save(new Conversation()));
    }

    private String retrieveContext(String query) {
        try {
            List<EmbeddingMatch<TextSegment>> matches = searchRelevant(query);
            return matches.stream()
                    .map(m -> m.embedded().text())
                    .reduce((a, b) -> a + "\n\n---\n\n" + b)
                    .orElse("");
        } catch (Exception e) {
            log.warn("知识库检索失败: {}", e.getMessage());
            return "";
        }
    }

    private List<String> retrieveSources(String query) {
        try {
            List<EmbeddingMatch<TextSegment>> matches = searchRelevant(query);
            return matches.stream()
                    .map(m -> String.format("[文档%s·片段%s] 相似度:%.2f",
                            m.embedded().metadata().getString("document_id"),
                            m.embedded().metadata().getString("chunk_index"),
                            m.score()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<EmbeddingMatch<TextSegment>> searchRelevant(String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(similarityThreshold)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);
        return result.matches();
    }

    private List<ChatMessage> buildChatMessages(
            List<Message> history, String context, String currentQuery) {
        List<ChatMessage> messages = new ArrayList<>();

        // 系统提示词
        String systemPrompt = buildSystemPrompt(context);
        messages.add(SystemMessage.from(systemPrompt));

        // 历史消息（排除最后一条，即当前用户消息）
        for (int i = 0; i < history.size() - 1; i++) {
            Message msg = history.get(i);
            if ("USER".equals(msg.getRole())) {
                messages.add(UserMessage.from(msg.getContent()));
            } else {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }

        // 当前用户消息
        messages.add(UserMessage.from(currentQuery));

        return messages;
    }

    private String buildSystemPrompt(String context) {
        if (context == null || context.isEmpty()) {
            return "你是一个企业知识库助手.\n"
                    + "当前知识库中暂无与问题直接匹配的资料.\n"
                    + "对于常识性问题, 你可以基于自身知识回答, 但需说明"
                    + "\"以下回答来自通用知识, 非企业知识库内容\".\n"
                    + "回答请简洁, 专业.";
        }
        return "你是一个企业知识库助手.请严格基于以下知识库内容回答用户问题.\n\n"
                + "[规则]\n"
                + "- 优先从知识库中寻找答案, 准确引用\n"
                + "- 知识库内容不足以回答时, 如实告知, 绝不编造\n"
                + "- 回答简洁, 专业\n\n"
                + "[知识库相关内容]\n"
                + context;
    }
}
