package com.zhangzhewen.ragdemo.adapter.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 需求指定 MD5 兼容值测试。 */
class Md5PasswordEncoderTest {
    /** 123456 必须编码为固定小写十六进制。 */ @Test void fixedValue(){Md5PasswordEncoder encoder=new Md5PasswordEncoder();assertThat(encoder.encode("123456")).isEqualTo("e10adc3949ba59abbe56e057f20f883e");assertThat(encoder.matches("123456","e10adc3949ba59abbe56e057f20f883e")).isTrue();}
}
