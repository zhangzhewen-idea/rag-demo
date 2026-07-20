package com.zhangzhewen.ragdemo.application.knowledge;

import com.zhangzhewen.ragdemo.domain.gateway.*;
import com.zhangzhewen.ragdemo.domain.knowledge.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.*;

/** 独立异步文档解析和向量化协作者。 */
@Component
public class DocumentIngestionWorker {
    private final DocumentGateway documents;private final DocumentParserGateway parser;private final VectorGateway vectors;private final IngestionPolicy policy;
    /** 注入依赖。 */ public DocumentIngestionWorker(DocumentGateway documents,DocumentParserGateway parser,VectorGateway vectors,IngestionPolicy policy){this.documents=documents;this.parser=parser;this.vectors=vectors;this.policy=policy;}
    /** 条件抢占任务后解析、分批向量化，并在任一失败时删除本次向量。 */
    @Async("documentTaskExecutor") public void process(Long documentId){KnowledgeDocument document=documents.findDocumentById(documentId).orElse(null);if(document==null)return;DocumentStatus expected=document.status()==DocumentStatus.FAILED?DocumentStatus.FAILED:DocumentStatus.PENDING;if(!documents.transit(documentId,expected,DocumentStatus.PROCESSING))return;try{List<DocumentParserGateway.ParsedChunk> chunks=parser.parse(document.storagePath(),document.extension(),policy.chunkSize(),policy.chunkOverlap());for(int start=0;start<chunks.size();start+=policy.embeddingBatchSize()){int end=Math.min(chunks.size(),start+policy.embeddingBatchSize());List<String> texts=new ArrayList<>();List<Map<String,Object>> metadata=new ArrayList<>();for(int i=start;i<end;i++){var c=chunks.get(i);texts.add(c.text());Map<String,Object> m=new HashMap<>(c.metadata());m.put("knowledgeBaseId",document.knowledgeBaseId().toString());m.put("documentId",document.id().toString());m.put("chunkIndex",i);m.put("sourceName",document.originalName());metadata.add(m);}vectors.add(texts,metadata);}documents.markReady(documentId,chunks.size());}catch(Exception e){try{vectors.deleteByDocumentId(documentId);}catch(Exception ignored){/* 保留原始失败原因，删除可由管理员重试补偿。 */}documents.markFailed(documentId,"INGESTION",e.getMessage(),true);}}
}
