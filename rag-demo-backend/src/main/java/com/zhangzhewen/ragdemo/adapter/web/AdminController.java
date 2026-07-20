package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.application.dto.*;
import com.zhangzhewen.ragdemo.application.knowledge.*;
import com.zhangzhewen.ragdemo.application.user.UserService;
import com.zhangzhewen.ragdemo.domain.gateway.DashboardGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

/** 管理员看板、用户、知识库和文档接口集合。 */
@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final DashboardGateway dashboard;private final UserService users;private final KnowledgeService knowledge;private final DocumentService documents;
    /** 注入服务。 */ public AdminController(DashboardGateway dashboard,UserService users,KnowledgeService knowledge,DocumentService documents){this.dashboard=dashboard;this.users=users;this.knowledge=knowledge;this.documents=documents;}
    /** 返回真实 SQL 聚合看板。 */ @GetMapping("/dashboard") public ApiResponse<Map<String,Object>> dashboard(){return WebSupport.ok(dashboard.snapshot());}
    /** 用户列表。 */ @GetMapping("/users") public ApiResponse<List<AuthDtos.UserView>> users(){return WebSupport.ok(users.list());}
    /** 上传用户头像。 */ @PostMapping("/users/avatars") public ApiResponse<Map<String,String>> uploadAvatar(@RequestPart("file")MultipartFile file)throws IOException{return WebSupport.ok(Map.of("url",users.uploadAvatar(Objects.requireNonNullElse(file.getOriginalFilename(),"avatar"),Objects.requireNonNullElse(file.getContentType(),"application/octet-stream"),file.getBytes())));}
    /** 新增用户。 */ @PostMapping("/users") public ApiResponse<Map<String,Long>> createUser(@Valid @RequestBody UserDtos.CreateRequest r){return WebSupport.ok(Map.of("id",users.create(r)));}
    /** 修改用户。 */ @PutMapping("/users/{id}") public ApiResponse<Void> updateUser(@PathVariable Long id,@Valid @RequestBody UserDtos.UpdateRequest r){users.update(id,r);return WebSupport.ok(null);}
    /** 重置用户密码。 */ @PostMapping("/users/{id}/reset-password") public ApiResponse<Void> reset(@PathVariable Long id,@RequestBody(required=false) UserDtos.ResetPasswordRequest r){users.reset(id,r==null?null:r.password());return WebSupport.ok(null);}
    /** 全部知识库。 */ @GetMapping("/knowledge-bases") public ApiResponse<List<KnowledgeDtos.View>> knowledge(){return WebSupport.ok(knowledge.listAll());}
    /** 新增知识库。 */ @PostMapping("/knowledge-bases") public ApiResponse<Map<String,Long>> createKb(@Valid @RequestBody KnowledgeDtos.SaveRequest r,Authentication a){return WebSupport.ok(Map.of("id",knowledge.create(r,WebSupport.userId(a))));}
    /** 修改知识库。 */ @PutMapping("/knowledge-bases/{id}") public ApiResponse<Void> updateKb(@PathVariable Long id,@Valid @RequestBody KnowledgeDtos.SaveRequest r){knowledge.update(id,r);return WebSupport.ok(null);}
    /** 上传知识库封面。 */ @PostMapping("/knowledge-bases/covers") public ApiResponse<Map<String,String>> uploadCover(@RequestPart("file")MultipartFile file)throws IOException{return WebSupport.ok(Map.of("url",knowledge.uploadCover(Objects.requireNonNullElse(file.getOriginalFilename(),"cover"),Objects.requireNonNullElse(file.getContentType(),"application/octet-stream"),file.getBytes())));}
    /** 删除空知识库。 */ @DeleteMapping("/knowledge-bases/{id}") public ApiResponse<Void> deleteKb(@PathVariable Long id){knowledge.delete(id);return WebSupport.ok(null);}
    /** 上传并创建异步任务。 */ @PostMapping("/knowledge-bases/{id}/documents") public ApiResponse<Map<String,Long>> upload(@PathVariable Long id,@RequestPart("file")MultipartFile file,Authentication a)throws IOException{return WebSupport.ok(Map.of("id",documents.upload(id,Objects.requireNonNullElse(file.getOriginalFilename(),"upload"),file.getContentType(),file.getBytes(),WebSupport.userId(a))));}
    /** 文档列表。 */ @GetMapping("/knowledge-bases/{id}/documents") public ApiResponse<List<KnowledgeDocument>> documents(@PathVariable Long id){return WebSupport.ok(documents.list(id));}
    /** 文档详情。 */ @GetMapping("/documents/{id}") public ApiResponse<KnowledgeDocument> document(@PathVariable Long id){return WebSupport.ok(documents.detail(id));}
    /** 重试失败入库。 */ @PostMapping("/documents/{id}/retry") public ApiResponse<Void> retry(@PathVariable Long id){documents.retry(id);return WebSupport.ok(null);}
    /** 删除文档。 */ @DeleteMapping("/documents/{id}") public ApiResponse<Void> deleteDocument(@PathVariable Long id){documents.delete(id);return WebSupport.ok(null);}
}
