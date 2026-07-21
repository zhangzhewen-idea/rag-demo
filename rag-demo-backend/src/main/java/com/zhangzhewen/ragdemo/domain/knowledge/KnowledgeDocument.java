package com.zhangzhewen.ragdemo.domain.knowledge;

/**
 * 文档任务领域对象。
 */
public record KnowledgeDocument(Long id, Long knowledgeBaseId, String originalName,
                                String storedName,
                                String storagePath, String extension, String mimeType,
                                long fileSize, String contentHash,
                                DocumentStatus status, int chunkCount, int retryCount,
                                String failureStage, String failureReason) {

}
