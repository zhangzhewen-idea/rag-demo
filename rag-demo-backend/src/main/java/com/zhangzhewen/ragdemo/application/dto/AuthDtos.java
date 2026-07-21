package com.zhangzhewen.ragdemo.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * 认证接口 DTO 集合。
 */
public final class AuthDtos {

  private AuthDtos() {
  }

  /**
   * 登录请求。
   */
  public record LoginRequest(
      @NotBlank(message = "请输入账号") @Size(max = 64, message = "账号不能超过 64 个字符") String username,
      @NotBlank(message = "请输入密码") String password) {

  }

  /**
   * 登录结果。
   */
  public record LoginResponse(String accessToken, long expiresInSeconds, UserView user) {

  }

  /**
   * 当前用户视图。
   */
  public record UserView(Long id, String username, String nickname, String avatarUrl,
                         Set<String> roles, String status) {

  }
}
