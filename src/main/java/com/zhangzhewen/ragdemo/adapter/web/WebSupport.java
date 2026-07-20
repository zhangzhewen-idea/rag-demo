package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.application.dto.ApiResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;

/** Web 适配层公共转换。 */
public final class WebSupport {
    private WebSupport() { }
    /** 构造统一成功响应。 */ public static <T> ApiResponse<T> ok(T data){return ApiResponse.ok(data,traceId());}
    /** 获取当前 traceId。 */ public static String traceId(){return MDC.get("traceId")==null?"":MDC.get("traceId");}
    /** 提取 JWT principal 中的用户 ID。 */ public static Long userId(Authentication authentication){return (Long)authentication.getPrincipal();}
}
