package com.zhangzhewen.ragdemo.domain.knowledge;

/** 知识库领域对象。 */
public record KnowledgeBase(Long id, String name, String description, String coverUrl, KnowledgeBaseStatus status) {
    /** 判断知识库能否用于问答。
     * @return 启用时返回 true
     */
    public boolean searchable() { return status == KnowledgeBaseStatus.ENABLED; }
}
