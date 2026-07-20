package com.zhangzhewen.ragdemo.application.user;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.AuthDtos;
import com.zhangzhewen.ragdemo.application.dto.UserDtos;
import com.zhangzhewen.ragdemo.domain.gateway.UserGateway;
import com.zhangzhewen.ragdemo.domain.identity.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 管理员用户管理用例。 */
@Service @PreAuthorize("hasRole('ADMIN')")
public class UserService {
    private final UserGateway users;private final PasswordEncoder passwords;
    /** 注入依赖。 */ public UserService(UserGateway users,PasswordEncoder passwords){this.users=users;this.passwords=passwords;}
    /** 查询用户。 */ public List<AuthDtos.UserView> list(){return users.list().stream().map(this::view).toList();}
    /** 创建用户，默认密码为 123456。 */ public Long create(UserDtos.CreateRequest r){try{return users.create(r.username(),passwords.encode(r.password()==null||r.password().isBlank()?"123456":r.password()),r.nickname(),status(r.status()),roles(r.roles()));}catch(DuplicateKeyException e){throw new BusinessException("USERNAME_EXISTS","用户名已存在",HttpStatus.CONFLICT);}}
    /** 修改资料、角色和状态。 */ public void update(Long id,UserDtos.UpdateRequest r){users.findUserById(id).orElseThrow(()->new BusinessException("USER_NOT_FOUND","用户不存在",HttpStatus.NOT_FOUND));users.update(id,r.nickname(),status(r.status()),roles(r.roles()));}
    /** 重置密码，空值使用 123456。 */ public void reset(Long id,String raw){users.resetPassword(id,passwords.encode(raw==null||raw.isBlank()?"123456":raw));}
    private UserStatus status(String value){return value==null?UserStatus.ENABLED:UserStatus.valueOf(value);}
    private Set<Role> roles(Set<String> values){return values.stream().map(Role::valueOf).collect(Collectors.toSet());}
    private AuthDtos.UserView view(User u){return new AuthDtos.UserView(u.id(),u.username(),u.nickname(),u.avatarUrl(),u.roles().stream().map(Enum::name).collect(Collectors.toSet()),u.status().name());}
}
