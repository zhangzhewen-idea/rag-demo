package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.ApiResponse;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/** 将业务异常、校验异常和未知异常转换为稳定响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    /** 处理业务异常。 */ @ExceptionHandler(BusinessException.class) ResponseEntity<ApiResponse<Void>> business(BusinessException e){return ResponseEntity.status(e.status()).body(new ApiResponse<>(e.code(),e.getMessage(),null,WebSupport.traceId()));}
    /** 处理参数校验异常。 */ @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e){String message=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+" "+x.getDefaultMessage()).orElse("请求参数错误");return ResponseEntity.badRequest().body(new ApiResponse<>("VALIDATION_ERROR",message,null,WebSupport.traceId()));}
    /** 隐藏内部细节但记录 traceId。 */ @ExceptionHandler(Exception.class) ResponseEntity<ApiResponse<Void>> unknown(Exception e){log.error("未处理异常 traceId={}",WebSupport.traceId(),e);return ResponseEntity.status(500).body(new ApiResponse<>("INTERNAL_ERROR","系统暂时不可用",null,WebSupport.traceId()));}
}
