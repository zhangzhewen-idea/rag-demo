package com.zhangzhewen.ragdemo.domain.gateway;

import java.time.Duration;

/** Redis 认证状态边界。 */
public interface AuthStateGateway {
    /** 保存 Refresh Token。 */ void saveRefresh(String tokenId, Long userId, Duration ttl);
    /** 读取 Refresh Token 对应用户。 */ Long getRefreshUser(String tokenId);
    /** 删除 Refresh Token。 */ void deleteRefresh(String tokenId);
    /** 使 Access Token 失效。 */ void blacklist(String jti, Duration ttl);
    /** 判断 Access Token 是否失效。 */ boolean blacklisted(String jti);
    /** 记录失败并返回失败次数。 */ long recordLoginFailure(String username, Duration ttl);
    /** 清除失败次数。 */ void clearLoginFailures(String username);
    /** 当前失败次数。 */ long loginFailures(String username);
}
