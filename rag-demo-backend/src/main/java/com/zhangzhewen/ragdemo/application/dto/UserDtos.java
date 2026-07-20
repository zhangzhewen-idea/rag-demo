package com.zhangzhewen.ragdemo.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** 用户管理 DTO 集合。 */
public final class UserDtos {
    private UserDtos() { }
    /** 新增用户。 */ public record CreateRequest(
            @NotBlank(message = "请输入账号") @Size(max = 64, message = "账号不能超过 64 个字符") String username,
            @NotBlank(message = "请输入昵称") @Size(max = 64, message = "昵称不能超过 64 个字符") String nickname,
            @Size(max = 100, message = "初始密码不能超过 100 个字符") String password,
            String status,
            @NotEmpty(message = "请至少选择一个角色") Set<String> roles) { }
    /** 修改用户。 */ public record UpdateRequest(
            @NotBlank(message = "请输入昵称") @Size(max = 64, message = "昵称不能超过 64 个字符") String nickname,
            String status,
            @NotEmpty(message = "请至少选择一个角色") Set<String> roles) { }
    /** 重置密码。 */ public record ResetPasswordRequest(String password) { }
}
