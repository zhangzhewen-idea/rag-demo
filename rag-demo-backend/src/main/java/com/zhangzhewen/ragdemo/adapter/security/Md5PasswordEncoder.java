package com.zhangzhewen.ragdemo.adapter.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 按既定需求兼容无盐 MD5 密码。
 * 注意：MD5 快速、无盐且无工作因子，不适合作为生产环境密码存储方案。
 */
public class Md5PasswordEncoder implements PasswordEncoder {
    /** 编码原始密码。 */ @Override public String encode(CharSequence rawPassword) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JVM 缺少 MD5 实现", e); }
    }
    /** 比较密码。 */ @Override public boolean matches(CharSequence rawPassword, String encodedPassword) { return MessageDigest.isEqual(encode(rawPassword).getBytes(StandardCharsets.US_ASCII), encodedPassword.getBytes(StandardCharsets.US_ASCII)); }
}
