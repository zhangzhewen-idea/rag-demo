package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.adapter.security.JwtService;
import com.zhangzhewen.ragdemo.application.auth.AuthService;
import com.zhangzhewen.ragdemo.application.dto.*;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.Map;

/** 登录、刷新、退出和当前用户接口。 */
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private static final String COOKIE="rag_refresh_token";private final AuthService auth;private final JwtService jwt;
    /** 注入依赖。 */ public AuthController(AuthService auth,JwtService jwt){this.auth=auth;this.jwt=jwt;}
    /** 登录并通过 HttpOnly Cookie 下发 Refresh Token。 */ @PostMapping("/login") public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest r,HttpServletResponse response){return response(auth.login(r.username(),r.password()),response);}
    /** 轮换 Refresh Token。 */ @PostMapping("/refresh") public ApiResponse<AuthDtos.LoginResponse> refresh(@CookieValue(name=COOKIE,required=false)String refresh,HttpServletResponse response){return response(auth.refresh(refresh),response);}
    /** 注销服务端状态和当前 Access Token。 */ @PostMapping("/logout") public ApiResponse<Map<String,Boolean>> logout(@CookieValue(name=COOKIE,required=false)String refresh,@RequestHeader(name="Authorization",required=false)String authorization,HttpServletResponse response){String jti=null;Instant expiry=null;if(authorization!=null&&authorization.startsWith("Bearer ")){Claims c=jwt.parse(authorization.substring(7));jti=c.getId();expiry=c.getExpiration().toInstant();}auth.logout(refresh,jti,expiry);response.addHeader(HttpHeaders.SET_COOKIE,cookie("",Duration.ZERO).toString());return WebSupport.ok(Map.of("loggedOut",true));}
    /** 返回当前登录用户。 */ @GetMapping("/me") public ApiResponse<AuthDtos.UserView> me(Authentication authentication){return WebSupport.ok(auth.me(WebSupport.userId(authentication)));}
    private ApiResponse<AuthDtos.LoginResponse> response(AuthService.Session session,HttpServletResponse response){response.addHeader(HttpHeaders.SET_COOKIE,cookie(session.refreshToken(),Duration.ofDays(7)).toString());long seconds=Math.max(0,Duration.between(Instant.now(),session.accessExpiresAt()).toSeconds());return WebSupport.ok(new AuthDtos.LoginResponse(session.accessToken(),seconds,session.user()));}
    private ResponseCookie cookie(String value,Duration age){return ResponseCookie.from(COOKIE,value).httpOnly(true).sameSite("Lax").path("/api/auth").maxAge(age).build();}
}
