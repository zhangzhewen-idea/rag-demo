package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.application.user.UserService;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 公开读取用户头像。 */
@RestController @RequestMapping("/api/avatars")
public class AvatarController {
    private final UserService users;
    /** 注入用户用例。 */ public AvatarController(UserService users){this.users=users;}
    /** 返回头像图片。 */ @GetMapping("/{fileName:.+}") public ResponseEntity<byte[]> avatar(@PathVariable String fileName){var avatar=users.avatar(fileName);return ResponseEntity.ok().contentType(MediaType.parseMediaType(avatar.contentType())).cacheControl(CacheControl.maxAge(Duration.ofDays(7))).body(avatar.content());}
}
