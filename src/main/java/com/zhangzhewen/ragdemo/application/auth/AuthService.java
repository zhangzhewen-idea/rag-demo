package com.zhangzhewen.ragdemo.application.auth;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.AuthDtos;
import com.zhangzhewen.ragdemo.domain.gateway.AuthStateGateway;
import com.zhangzhewen.ragdemo.domain.gateway.TokenIssuerGateway;
import com.zhangzhewen.ragdemo.domain.gateway.UserGateway;
import com.zhangzhewen.ragdemo.domain.identity.User;
import com.zhangzhewen.ragdemo.domain.identity.AuthPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** 登录、刷新和退出用例编排。 */
@Service
public class AuthService {
    private static final int MAX_FAILURES=5; private static final Duration LOCK_TIME=Duration.ofMinutes(15);
    private final UserGateway users; private final AuthStateGateway authState; private final PasswordEncoder passwords; private final TokenIssuerGateway tokens; private final AuthPolicy policy;
    /** 注入认证依赖。 */ public AuthService(UserGateway users,AuthStateGateway authState,PasswordEncoder passwords,TokenIssuerGateway tokens,AuthPolicy policy){this.users=users;this.authState=authState;this.passwords=passwords;this.tokens=tokens;this.policy=policy;}
    /** 验证账号并签发 Access/Refresh Token。 */
    public Session login(String username,String password){
        if(authState.loginFailures(username)>=MAX_FAILURES) throw new BusinessException("LOGIN_LOCKED","登录失败次数过多，请 15 分钟后重试",HttpStatus.TOO_MANY_REQUESTS);
        User user=users.findByUsername(username).orElse(null);
        if(user==null||!passwords.matches(password,user.password())){authState.recordLoginFailure(username,LOCK_TIME);throw new BusinessException("BAD_CREDENTIALS","账号或密码错误",HttpStatus.UNAUTHORIZED);}
        if(!user.enabled())throw new BusinessException("USER_DISABLED","账号已停用",HttpStatus.FORBIDDEN);
        authState.clearLoginFailures(username);users.touchLastLogin(user.id());return issue(user);
    }
    /** 使用服务端有效 Refresh Token 恢复登录态。 */
    public Session refresh(String refreshToken){Long userId=refreshToken==null?null:authState.getRefreshUser(refreshToken);if(userId==null)throw new BusinessException("REFRESH_INVALID","登录状态已失效",HttpStatus.UNAUTHORIZED);User user=users.findUserById(userId).filter(User::enabled).orElseThrow(()->new BusinessException("USER_DISABLED","账号已停用",HttpStatus.FORBIDDEN));authState.deleteRefresh(refreshToken);return issue(user);}
    /** 注销 Refresh Token 并拉黑当前 Access Token。 */
    public void logout(String refreshToken,String jti,Instant expiry){if(refreshToken!=null)authState.deleteRefresh(refreshToken);if(jti!=null&&expiry!=null)authState.blacklist(jti,Duration.between(Instant.now(),expiry));}
    /** 查询当前用户。 */ public AuthDtos.UserView me(Long userId){return view(users.findUserById(userId).orElseThrow(()->new BusinessException("USER_NOT_FOUND","用户不存在",HttpStatus.NOT_FOUND)));}
    private Session issue(User user){TokenIssuerGateway.IssuedToken access=tokens.issueAccess(user);String refresh=UUID.randomUUID().toString();authState.saveRefresh(refresh,user.id(),policy.refreshTtl());return new Session(access.value(),access.expiresAt(),refresh,view(user));}
    private AuthDtos.UserView view(User u){return new AuthDtos.UserView(u.id(),u.username(),u.nickname(),u.avatarUrl(),u.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),u.status().name());}
    /** 认证会话。 */ public record Session(String accessToken,Instant accessExpiresAt,String refreshToken,AuthDtos.UserView user){}
}
