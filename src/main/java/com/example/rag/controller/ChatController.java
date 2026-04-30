package com.example.rag.controller;

import com.example.rag.model.dto.ApiResponse;
import com.example.rag.model.dto.ChatRequest;
import com.example.rag.model.dto.ChatResponse;
import com.example.rag.model.entity.Conversation;
import com.example.rag.model.entity.Message;
import com.example.rag.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public ApiResponse<ChatResponse> send(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(request);
        return ApiResponse.success(response);
    }

    @GetMapping("/conversations")
    public ApiResponse<List<Conversation>> listConversations() {
        return ApiResponse.success(chatService.listConversations());
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<Message>> getMessages(@PathVariable Long id) {
        return ApiResponse.success(chatService.getMessages(id));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return ApiResponse.success();
    }
}
