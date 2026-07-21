package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.application.dto.ApiResponse;
import com.zhangzhewen.ragdemo.application.dto.KnowledgeDtos;
import com.zhangzhewen.ragdemo.application.knowledge.KnowledgeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户知识库浏览接口。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeController {

  private final KnowledgeService service;

  /**
   * 注入服务。
   */
  public KnowledgeController(KnowledgeService service) {
    this.service = service;
  }

  /**
   * 查询全部已启用知识库。
   */
  @GetMapping
  public ApiResponse<List<KnowledgeDtos.View>> list() {
    return WebSupport.ok(service.listEnabled());
  }
}
