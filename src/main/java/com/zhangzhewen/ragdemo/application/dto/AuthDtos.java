package com.zhangzhewen.ragdemo.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

/** 认证接口 DTO 集合。 */
public final class AuthDtos {
    private AuthDtos() { }
    /** 登录请求。 */ public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
    /** 登录结果。 */ public record LoginResponse(String accessToken, long expiresInSeconds, UserView user) { }
    /** 当前用户视图。 */ public record UserView(Long id, String username, String nickname, String avatarUrl, Set<String> roles, String status) { }
}
