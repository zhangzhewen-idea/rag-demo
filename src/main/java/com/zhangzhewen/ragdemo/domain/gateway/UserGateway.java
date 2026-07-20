package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.identity.Role;
import com.zhangzhewen.ragdemo.domain.identity.User;
import com.zhangzhewen.ragdemo.domain.identity.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 用户持久化能力边界。 */
public interface UserGateway {
    /** 按用户名查询用户。 @param username 用户名 @return 用户 */ Optional<User> findByUsername(String username);
    /** 按 ID 查询用户。 @param id ID @return 用户 */ Optional<User> findUserById(Long id);
    /** 查询用户列表。 @return 用户列表 */ List<User> list();
    /** 创建用户。 @return 新用户 ID */ Long create(String username, String password, String nickname, UserStatus status, Set<Role> roles);
    /** 更新用户资料。 */ void update(Long id, String nickname, UserStatus status, Set<Role> roles);
    /** 重置密码。 */ void resetPassword(Long id, String encodedPassword);
    /** 记录登录时间。 */ void touchLastLogin(Long id);
}
