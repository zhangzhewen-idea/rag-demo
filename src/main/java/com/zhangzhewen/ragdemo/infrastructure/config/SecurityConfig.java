package com.zhangzhewen.ragdemo.adapter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Spring Security 无状态安全边界。 */
@Configuration
public class SecurityConfig {
    /** 注册需求兼容密码编码器。 */ @Bean PasswordEncoder passwordEncoder(){return new Md5PasswordEncoder();}
    /** 配置角色、异常响应与 JWT 过滤器。 */
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwt,ObjectMapper mapper)throws Exception{
        http.csrf(csrf->csrf.disable()).cors(Customizer.withDefaults()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/api/auth/refresh","/actuator/health").permitAll().requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->writeError(res,mapper,401,"AUTH_REQUIRED","请先登录")).accessDeniedHandler((req,res,ex)->writeError(res,mapper,403,"FORBIDDEN","没有访问权限")))
            .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private void writeError(HttpServletResponse response,ObjectMapper mapper,int status,String code,String message)throws java.io.IOException{response.setStatus(status);response.setCharacterEncoding(StandardCharsets.UTF_8.name());response.setContentType(MediaType.APPLICATION_JSON_VALUE);mapper.writeValue(response.getWriter(),Map.of("code",code,"message",message,"data",Map.of(),"traceId",java.util.UUID.randomUUID().toString()));}
    /** 仅允许本地 Vite 开发源携带 Refresh Cookie。 */ @Bean CorsConfigurationSource corsConfigurationSource(){CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(List.of("http://localhost:5173"));c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("*"));c.setAllowCredentials(true);UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
}
