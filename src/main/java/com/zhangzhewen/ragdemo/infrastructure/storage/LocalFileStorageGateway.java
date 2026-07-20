package com.zhangzhewen.ragdemo.infrastructure.storage;

import com.zhangzhewen.ragdemo.domain.gateway.FileStorageGateway;
import com.zhangzhewen.ragdemo.infrastructure.config.RagProperties;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/** 本地文件系统安全存储实现。 */
@Component
public class LocalFileStorageGateway implements FileStorageGateway {
    private final Path root;
    /** 固定并规范化上传根目录。 */ public LocalFileStorageGateway(RagProperties properties){this.root=Path.of(properties.storageRoot()).toAbsolutePath().normalize();}
    /** 使用 UUID 文件名并再次验证路径仍位于根目录。 */
    @Override public Path save(Long knowledgeBaseId,String extension,InputStream input){try{Path dir=safe(root.resolve(knowledgeBaseId.toString()));Files.createDirectories(dir);Path target=safe(dir.resolve(UUID.randomUUID()+"."+extension));Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING);return target;}catch(IOException e){throw new UncheckedIOException("文件保存失败",e);}}
    /** 防止数据库中的异常路径删除上传根目录外文件。 */
    @Override public void delete(String absolutePath){try{Files.deleteIfExists(safe(Path.of(absolutePath)));}catch(IOException e){throw new UncheckedIOException("文件删除失败",e);}}
    private Path safe(Path path){Path normalized=path.toAbsolutePath().normalize();if(!normalized.startsWith(root))throw new SecurityException("文件路径越界");return normalized;}
}
