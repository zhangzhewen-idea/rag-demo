package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.identity.User;
import java.time.Instant;

/** Access Token 签发能力边界。 */
public interface TokenIssuerGateway {
    /** 为用户签发短期 Access Token。 */ IssuedToken issueAccess(User user);
    /** 已签发 Token。 */ record IssuedToken(String value,String jti,Instant expiresAt) { }
}
