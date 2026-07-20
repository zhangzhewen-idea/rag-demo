package com.zhangzhewen.ragdemo.application;

import org.springframework.http.HttpStatus;

/** 可映射为稳定 HTTP 响应的业务异常。 */
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    /** 创建异常。 */ public BusinessException(String code, String message, HttpStatus status) { super(message); this.code = code; this.status = status; }
    /** 业务码。 */ public String code() { return code; }
    /** HTTP 状态。 */ public HttpStatus status() { return status; }
}
