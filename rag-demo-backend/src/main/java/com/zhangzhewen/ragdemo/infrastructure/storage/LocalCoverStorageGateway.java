package com.zhangzhewen.ragdemo.infrastructure.storage;

import com.zhangzhewen.ragdemo.domain.gateway.CoverStorageGateway;
import com.zhangzhewen.ragdemo.infrastructure.config.RagProperties;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** 本地知识库封面存储实现。 */
@Component
public class LocalCoverStorageGateway implements CoverStorageGateway {
  private static final Map<String,String> TYPES=Map.of("png","image/png","jpg","image/jpeg","jpeg","image/jpeg","webp","image/webp");
  private final Path root;
  /** 封面固定保存到上传根目录下的 covers 子目录。 */ public LocalCoverStorageGateway(RagProperties properties){root=Path.of(properties.storageRoot(),"covers").toAbsolutePath().normalize();}
  @Override public String save(String extension,InputStream input){try{Files.createDirectories(root);Path target=safe(root.resolve(UUID.randomUUID()+"."+extension));Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING);return "/api/covers/"+target.getFileName();}catch(IOException e){throw new UncheckedIOException("封面保存失败",e);}}
  @Override public StoredCover load(String fileName){try{Path path=safe(root.resolve(fileName));String type=TYPES.get(extension(fileName));if(!Files.isRegularFile(path)||type==null)throw new FileNotFoundException(fileName);return new StoredCover(Files.readAllBytes(path),type);}catch(IOException e){throw new UncheckedIOException("封面读取失败",e);}}
  private Path safe(Path path){Path normalized=path.toAbsolutePath().normalize();if(!normalized.startsWith(root))throw new SecurityException("封面路径越界");return normalized;}
  private String extension(String name){int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
}
