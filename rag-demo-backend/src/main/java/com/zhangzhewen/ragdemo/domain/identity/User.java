package com.zhangzhewen.ragdemo.domain.identity;

import java.util.Set;

/**
 * 系统用户领域对象，集中表达账号状态和角色判断。
 */
public record User(Long id, String username, String password, String nickname, String avatarUrl,
                   UserStatus status, Set<Role> roles) {

  /**
   * 判断账号是否允许登录。
   *
   * @return 启用时返回 true
   */
  public boolean enabled() {
    return status == UserStatus.ENABLED;
  }

  /**
   * 判断用户是否拥有角色。
   *
   * @param role 目标角色
   * @return 是否拥有
   */
  public boolean hasRole(Role role) {
    return roles != null && roles.contains(role);
  }
}
