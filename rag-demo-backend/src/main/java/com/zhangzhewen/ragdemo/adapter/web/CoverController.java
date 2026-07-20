package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.application.knowledge.KnowledgeService;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 公开读取知识库封面。 */
@RestController @RequestMapping("/api/covers")
public class CoverController {
  private final KnowledgeService knowledge;
  /** 注入知识库用例。 */ public CoverController(KnowledgeService knowledge){this.knowledge=knowledge;}
  /** 返回封面图片。 */ @GetMapping("/{fileName:.+}") public ResponseEntity<byte[]> cover(@PathVariable String fileName){var cover=knowledge.cover(fileName);return ResponseEntity.ok().contentType(MediaType.parseMediaType(cover.contentType())).cacheControl(CacheControl.maxAge(Duration.ofDays(7))).body(cover.content());}
}
