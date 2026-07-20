package com.zhangzhewen.ragdemo.domain.gateway;

import java.io.InputStream;

/** 用户头像图片存储边界。 */
public interface AvatarStorageGateway {
    /** 保存头像并返回公开访问路径。 */ String save(String extension, InputStream inputStream);
    /** 读取已保存头像。 */ StoredAvatar load(String fileName);
    /** 头像二进制与媒体类型。 */ record StoredAvatar(byte[] content, String contentType) { }
}
