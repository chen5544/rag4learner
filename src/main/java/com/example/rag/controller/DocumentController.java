package com.example.rag.controller;

import com.example.rag.model.dto.ApiResponse;
import com.example.rag.model.dto.DocumentInfo;
import com.example.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ApiResponse<DocumentInfo> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件不能为空");
        }
        DocumentInfo info = documentService.upload(file);
        return ApiResponse.success(info);
    }

    @GetMapping
    public ApiResponse<List<DocumentInfo>> list() {
        return ApiResponse.success(documentService.list());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.success();
    }
}
