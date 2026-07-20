package com.zhangzhewen.ragdemo.application.user;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.AuthDtos;
import com.zhangzhewen.ragdemo.application.dto.UserDtos;
import com.zhangzhewen.ragdemo.domain.gateway.UserGateway;
import com.zhangzhewen.ragdemo.domain.gateway.AvatarStorageGateway;
import com.zhangzhewen.ragdemo.domain.identity.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;

/** 管理员用户管理用例。 */
@Service
public class UserService {
    private static final long MAX_AVATAR_SIZE=5L*1024*1024;private static final Map<String,String> AVATAR_TYPES=Map.of("png","image/png","jpg","image/jpeg","jpeg","image/jpeg","webp","image/webp");
    private final UserGateway users;private final PasswordEncoder passwords;private final AvatarStorageGateway avatars;
    /** 注入依赖。 */ public UserService(UserGateway users,PasswordEncoder passwords,AvatarStorageGateway avatars){this.users=users;this.passwords=passwords;this.avatars=avatars;}
    /** 查询用户。 */ @PreAuthorize("hasRole('ADMIN')") public List<AuthDtos.UserView> list(){return users.list().stream().map(this::view).toList();}
    /** 创建用户，默认密码为 123456。 */ @PreAuthorize("hasRole('ADMIN')") public Long create(UserDtos.CreateRequest r){try{return users.create(r.username(),passwords.encode(r.password()==null||r.password().isBlank()?"123456":r.password()),r.nickname(),r.avatarUrl(),status(r.status()),roles(r.roles()));}catch(DuplicateKeyException e){throw new BusinessException("USERNAME_EXISTS","用户名已存在",HttpStatus.CONFLICT);}}
    /** 修改资料、角色和状态。 */ @PreAuthorize("hasRole('ADMIN')") public void update(Long id,UserDtos.UpdateRequest r){users.findUserById(id).orElseThrow(()->new BusinessException("USER_NOT_FOUND","用户不存在",HttpStatus.NOT_FOUND));users.update(id,r.nickname(),r.avatarUrl(),status(r.status()),roles(r.roles()));}
    /** 重置密码，空值使用 123456。 */ @PreAuthorize("hasRole('ADMIN')") public void reset(Long id,String raw){users.resetPassword(id,passwords.encode(raw==null||raw.isBlank()?"123456":raw));}
    /** 校验并保存用户头像。 */ @PreAuthorize("hasRole('ADMIN')") public String uploadAvatar(String originalName,String contentType,byte[] bytes){if(bytes.length==0||bytes.length>MAX_AVATAR_SIZE)throw new BusinessException("AVATAR_SIZE_INVALID","头像必须大于 0 且不超过 5 MB",HttpStatus.BAD_REQUEST);String extension=extension(originalName);if(!AVATAR_TYPES.containsKey(extension)||!AVATAR_TYPES.get(extension).equalsIgnoreCase(contentType))throw new BusinessException("AVATAR_TYPE_UNSUPPORTED","仅支持 PNG、JPEG、WebP 图片",HttpStatus.BAD_REQUEST);if(!signatureMatches(extension,bytes))throw new BusinessException("AVATAR_CONTENT_MISMATCH","图片内容与扩展名不匹配",HttpStatus.BAD_REQUEST);return avatars.save(extension,new ByteArrayInputStream(bytes));}
    /** 读取公开头像。 */ public AvatarStorageGateway.StoredAvatar avatar(String fileName){return avatars.load(fileName);}
    private UserStatus status(String value){return value==null?UserStatus.ENABLED:UserStatus.valueOf(value);}
    private Set<Role> roles(Set<String> values){return values.stream().map(Role::valueOf).collect(Collectors.toSet());}
    private AuthDtos.UserView view(User u){return new AuthDtos.UserView(u.id(),u.username(),u.nickname(),u.avatarUrl(),u.roles().stream().map(Enum::name).collect(Collectors.toSet()),u.status().name());}
    private String extension(String name){int dot=name==null?-1:name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
    private boolean signatureMatches(String extension,byte[] bytes){if(extension.equals("png"))return bytes.length>=8&&(bytes[0]&255)==0x89&&bytes[1]=='P'&&bytes[2]=='N'&&bytes[3]=='G';if(extension.equals("jpg")||extension.equals("jpeg"))return bytes.length>=3&&(bytes[0]&255)==0xff&&(bytes[1]&255)==0xd8&&(bytes[2]&255)==0xff;return bytes.length>=12&&bytes[0]=='R'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='F'&&bytes[8]=='W'&&bytes[9]=='E'&&bytes[10]=='B'&&bytes[11]=='P';}
}
