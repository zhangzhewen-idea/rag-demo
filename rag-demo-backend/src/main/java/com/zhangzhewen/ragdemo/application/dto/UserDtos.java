package com.zhangzhewen.ragdemo.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

/** 用户管理 DTO 集合。 */
public final class UserDtos {
    private UserDtos() { }
    /** 新增用户。 */ public record CreateRequest(@NotBlank String username, @NotBlank String nickname, String password, String status, @NotEmpty Set<String> roles) { }
    /** 修改用户。 */ public record UpdateRequest(@NotBlank String nickname, String status, @NotEmpty Set<String> roles) { }
    /** 重置密码。 */ public record ResetPasswordRequest(String password) { }
}
