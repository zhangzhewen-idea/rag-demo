package com.zhangzhewen.ragdemo.adapter.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** 为请求、日志和错误响应建立统一 traceId。 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {
    /** 在请求生命周期内设置并最终清理 MDC。 */ @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{String traceId=OptionalHeader.of(request.getHeader("X-Trace-Id"));MDC.put("traceId",traceId);response.setHeader("X-Trace-Id",traceId);try{chain.doFilter(request,response);}finally{MDC.remove("traceId");}}
    private static final class OptionalHeader{private static String of(String value){return value==null||value.isBlank()?UUID.randomUUID().toString():value;}}
}
