package com.zhangzhewen.ragdemo.application.knowledge;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.KnowledgeDtos;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.FileStorageGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.util.List;

/** 知识库查询与管理用例。 */
@Service
public class KnowledgeService {
    private final KnowledgeGateway knowledge;private final DocumentGateway documents;private final VectorGateway vectors;private final FileStorageGateway files;
    /** 注入依赖。 */ public KnowledgeService(KnowledgeGateway knowledge,DocumentGateway documents,VectorGateway vectors,FileStorageGateway files){this.knowledge=knowledge;this.documents=documents;this.vectors=vectors;this.files=files;}
    /** 查询普通用户可用知识库。 */ public List<KnowledgeDtos.View> listEnabled(){return knowledge.listEnabled().stream().map(this::view).toList();}
    /** 管理员查询全部。 */ @PreAuthorize("hasRole('ADMIN')") public List<KnowledgeDtos.View> listAll(){return knowledge.listAll().stream().map(this::view).toList();}
    /** 创建知识库。 */ @PreAuthorize("hasRole('ADMIN')") public Long create(KnowledgeDtos.SaveRequest r,Long userId){return knowledge.create(r.name(),r.description(),r.coverUrl(),userId);}
    /** 修改知识库。 */ @PreAuthorize("hasRole('ADMIN')") public void update(Long id,KnowledgeDtos.SaveRequest r){require(id);knowledge.update(id,r.name(),r.description(),r.coverUrl(),r.status()==null?KnowledgeBaseStatus.ENABLED:KnowledgeBaseStatus.valueOf(r.status()));}
    /** 删除前阻止处理中任务，并依次级联清理向量、文件和文档逻辑记录。 */
    @PreAuthorize("hasRole('ADMIN')") public void delete(Long id){require(id);if(documents.hasProcessing(id))throw new BusinessException("KB_HAS_PROCESSING_DOCUMENT","知识库存在处理中任务",HttpStatus.CONFLICT);for(KnowledgeDocument document:documents.listByKnowledgeBase(id)){if(!documents.transit(document.id(),document.status(),DocumentStatus.DELETING))throw new BusinessException("DOCUMENT_STATE_CONFLICT","文档状态已变化",HttpStatus.CONFLICT);try{vectors.deleteByDocumentId(document.id());files.delete(document.storagePath());documents.logicalDelete(document.id());}catch(Exception e){documents.markFailed(document.id(),"DELETE_KNOWLEDGE_BASE",e.getMessage(),false);throw new BusinessException("KB_DELETE_FAILED","知识库文档清理失败，可稍后重试",HttpStatus.INTERNAL_SERVER_ERROR);}}knowledge.deleteKnowledgeBase(id);}
    /** 获取可用于会话的知识库。 */ public KnowledgeBase requireSearchable(Long id){KnowledgeBase kb=require(id);if(!kb.searchable())throw new BusinessException("KB_DISABLED","知识库已停用",HttpStatus.CONFLICT);return kb;}
    private KnowledgeBase require(Long id){return knowledge.findKnowledgeById(id).orElseThrow(()->new BusinessException("KB_NOT_FOUND","知识库不存在",HttpStatus.NOT_FOUND));}
    private KnowledgeDtos.View view(KnowledgeBase k){return new KnowledgeDtos.View(k.id(),k.name(),k.description(),k.coverUrl(),k.status().name());}
}
