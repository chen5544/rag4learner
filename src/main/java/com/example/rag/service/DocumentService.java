package com.example.rag.service;

import com.example.rag.exception.BusinessException;
import com.example.rag.model.dto.DocumentInfo;
import com.example.rag.model.entity.Document;
import com.example.rag.repository.DocumentRepository;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private static final Tika TIKA = new Tika();

    @Value("${app.ai.rag.chunk-size}")
    private int chunkSize;

    @Value("${app.ai.rag.chunk-overlap}")
    private int chunkOverlap;

    public DocumentInfo upload(MultipartFile file) {
        // 1. 保存文档记录（状态: INDEXING）
        Document doc = Document.builder()
                .fileName(file.getOriginalFilename())
                .fileType(getExtension(file.getOriginalFilename()))
                .fileSize(file.getSize())
                .status("INDEXING")
                .build();
        doc = documentRepository.save(doc);
        Long documentId = doc.getId();

        try {
            // 2. 解析文档内容
            String content = TIKA.parseToString(file.getInputStream());
            doc.setContent(content);

            // 3. 文本分块
            dev.langchain4j.data.document.Document langDoc =
                    dev.langchain4j.data.document.Document.from(content);
            DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
            List<TextSegment> segments = splitter.split(langDoc);

            if (segments.isEmpty()) {
                throw new BusinessException("文档内容为空，无法提取有效文本");
            }

            // 4. 为每个分块附加文档 ID 元数据
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                segment.metadata().put("document_id", documentId.toString());
                segment.metadata().put("chunk_index", String.valueOf(i));
            }

            // 5. 向量化并存储
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);

            // 6. 更新文档状态
            doc.setChunkCount(segments.size());
            doc.setStatus("INDEXED");
            documentRepository.save(doc);

            log.info("文档索引完成: id={}, fileName={}, chunks={}",
                    documentId, file.getOriginalFilename(), segments.size());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档索引失败: id={}", documentId, e);
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
            throw new BusinessException("文档处理失败: " + e.getMessage());
        }

        return toInfo(doc);
    }

    public List<DocumentInfo> list() {
        return documentRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toInfo).toList();
    }

    public void delete(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        // 从向量库删除
        try {
            Filter filter = metadataKey("document_id").isEqualTo(id.toString());
            embeddingStore.removeAll(filter);
        } catch (Exception e) {
            log.warn("删除向量数据时出错（可能已经不存在）: {}", e.getMessage());
        }

        // 从数据库删除
        documentRepository.delete(doc);
        log.info("文档已删除: id={}, fileName={}", id, doc.getFileName());
    }

    private DocumentInfo toInfo(Document doc) {
        return DocumentInfo.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "unknown";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
