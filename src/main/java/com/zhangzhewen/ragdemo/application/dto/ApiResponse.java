package com.zhangzhewen.ragdemo.application.dto;

/** 普通 REST 接口统一响应。
 * @param code 稳定业务码
 * @param message 提示
 * @param data 数据
 * @param traceId 链路标识
 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {
    /** 构造成功响应。 */
    public static <T> ApiResponse<T> ok(T data, String traceId) { return new ApiResponse<>("0", "success", data, traceId); }
}
