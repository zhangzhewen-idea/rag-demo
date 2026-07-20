package com.zhangzhewen.ragdemo.application.conversation;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.domain.conversation.*;
import com.zhangzhewen.ragdemo.domain.gateway.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** 会话所有权、检索、拒答、流式生成和引用保存用例。 */
@Service
public class ConversationService {
    public static final String NO_EVIDENCE="当前知识库中未找到可靠依据";
    private final ConversationGateway conversations;private final VectorGateway vectors;private final AiGateway ai;private final KnowledgeGateway knowledge;private final RetrievalPolicy policy;
    /** 注入用例依赖。 */ public ConversationService(ConversationGateway conversations,VectorGateway vectors,AiGateway ai,KnowledgeGateway knowledge,RetrievalPolicy policy){this.conversations=conversations;this.vectors=vectors;this.ai=ai;this.knowledge=knowledge;this.policy=policy;}
    /** 创建绑定单一知识库的会话。 */ public Long create(Long userId,Long knowledgeBaseId,String title){requireSearchable(knowledgeBaseId);return conversations.create(userId,knowledgeBaseId,title==null||title.isBlank()?"新会话":title);}
    /** 查询本人会话。 */ public List<Conversation> list(Long userId){return conversations.listByUser(userId);}
    /** 查询会话及历史。 */ public Detail detail(Long id,Long userId){Conversation c=requireOwned(id,userId);return new Detail(c,conversations.recentMessages(id,100));}
    /** 重命名本人会话。 */ public void rename(Long id,Long userId,String title){requireOwned(id,userId);conversations.rename(id,title);}
    /** 删除本人会话。 */ public void delete(Long id,Long userId){requireOwned(id,userId);conversations.deleteConversation(id);}
    /** 异步执行流式回答，调用者负责把 delta 转为 SSE。 */
    public CompletableFuture<ChatResult> chat(Long conversationId,Long userId,String question,Consumer<String> delta){
        Conversation c=requireOwned(conversationId,userId);requireSearchable(c.knowledgeBaseId());conversations.saveMessage(conversationId,"USER",question,"COMPLETED",null,null,null);long start=System.nanoTime();
        List<RetrievedChunk> refs=vectors.search(c.knowledgeBaseId(),question,policy.topK(),policy.similarityThreshold());
        if(refs.isEmpty()){delta.accept(NO_EVIDENCE);Long id=conversations.saveMessage(conversationId,"ASSISTANT",NO_EVIDENCE,"COMPLETED",null,null,elapsed(start));return CompletableFuture.completedFuture(new ChatResult(id,NO_EVIDENCE,List.of(),elapsed(start)));}
        StringBuilder answer=new StringBuilder();return CompletableFuture.supplyAsync(()->{ai.streamAnswer(question,conversations.recentMessages(conversationId,20),refs,p->{answer.append(p);delta.accept(p);});long elapsed=elapsed(start);Long id=conversations.saveMessage(conversationId,"ASSISTANT",answer.toString(),"COMPLETED",null,null,elapsed);conversations.saveReferences(id,refs);return new ChatResult(id,answer.toString(),refs,elapsed);});
    }
    private long elapsed(long start){return (System.nanoTime()-start)/1_000_000;}
    private Conversation requireOwned(Long id,Long userId){Conversation c=conversations.findConversationById(id).orElseThrow(()->new BusinessException("CONVERSATION_NOT_FOUND","会话不存在",HttpStatus.NOT_FOUND));if(!c.ownedBy(userId))throw new BusinessException("CONVERSATION_FORBIDDEN","不能访问其他用户的会话",HttpStatus.FORBIDDEN);return c;}
    private void requireSearchable(Long id){var kb=knowledge.findKnowledgeById(id).orElseThrow(()->new BusinessException("KB_NOT_FOUND","知识库不存在",HttpStatus.NOT_FOUND));if(!kb.searchable())throw new BusinessException("KB_DISABLED","知识库已停用",HttpStatus.CONFLICT);}
    /** 会话详情。 */ public record Detail(Conversation conversation,List<Message> messages){}
    /** 回答完成结果。 */ public record ChatResult(Long messageId,String answer,List<RetrievedChunk> references,long elapsedMs){}
}
