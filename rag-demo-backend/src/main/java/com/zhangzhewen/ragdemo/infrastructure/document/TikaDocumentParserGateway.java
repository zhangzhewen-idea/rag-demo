package com.zhangzhewen.ragdemo.infrastructure.document;

import com.zhangzhewen.ragdemo.domain.gateway.DocumentParserGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.ChunkingConfig;
import com.zhangzhewen.ragdemo.domain.knowledge.TextChunkingPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Markdown 使用 Spring AI Reader，其余格式使用 Apache Tika 3。
 */
@Component
public class TikaDocumentParserGateway implements DocumentParserGateway {

  private final Tika tika = new Tika();
  private final TextChunkingPolicy customChunking;

  /**
   * 注入领域切片规则。
   */
  public TikaDocumentParserGateway(TextChunkingPolicy customChunking) {
    this.customChunking = customChunking;
  }

  /**
   * 检测文件内容对应 MIME。
   */
  @Override
  public String detect(String path) {
    try {
      return tika.detect(Path.of(path));
    } catch (Exception e) {
      throw new IllegalArgumentException("无法识别文件类型", e);
    }
  }

  @Override
  public String detect(String originalName, byte[] bytes) {
    try {
      return tika.detect(bytes, originalName);
    } catch (Exception e) {
      throw new IllegalArgumentException("无法识别文件类型", e);
    }
  }

  /**
   * 解析文本并以 TokenTextSplitter 为基础增加轻量相邻重叠。
   */
  @Override
  public List<ParsedChunk> parse(String path, String extension, ChunkingConfig config) {
    try {
      return chunk(load(new FileSystemResource(path), extension), config);
    } catch (Exception e) {
      throw new IllegalArgumentException("文档解析失败: " + e.getMessage(), e);
    }
  }

  @Override
  public List<ParsedChunk> preview(String originalName, String extension, byte[] bytes,
      ChunkingConfig config) {
    try {
      Resource resource = new ByteArrayResource(bytes) {
        @Override
        public String getFilename() {
          return originalName;
        }
      };
      return chunk(load(resource, extension), config);
    } catch (Exception e) {
      throw new IllegalArgumentException("文档解析失败: " + e.getMessage(), e);
    }
  }

  private List<Document> load(Resource resource, String extension) throws Exception {
    if ("md".equals(extension)) {
      var readerConfig = MarkdownDocumentReaderConfig.builder().withIncludeCodeBlock(true)
          .withIncludeBlockquote(true).build();
      return new MarkdownDocumentReader(resource, readerConfig).get();
    }
    try (var input = resource.getInputStream()) {
      return List.of(new Document(tika.parseToString(input)));
    }
  }

  private List<ParsedChunk> chunk(List<Document> source, ChunkingConfig config) {
    return config.strategy() == ChunkingConfig.Strategy.AUTO
        ? autoChunk(source, config) : customChunk(source, config);
  }

  private List<ParsedChunk> autoChunk(List<Document> source, ChunkingConfig config) {
    TokenTextSplitter splitter = TokenTextSplitter.builder()
        .withChunkSize(config.maxChunkLength()).withMinChunkSizeChars(50).build();
    List<Document> split = splitter.apply(source);
    List<ParsedChunk> result = new ArrayList<>();
    String previous = "";
    for (Document document : split) {
      String prefix = previous.isBlank() ? "" : previous.substring(
          Math.max(0, previous.length() - Math.min(previous.length(),
              config.overlapLength() * 3)));
      result.add(new ParsedChunk(prefix + document.getText(),
          new HashMap<>(document.getMetadata()), prefix.length()));
      previous = document.getText();
    }
    return result;
  }

  private List<ParsedChunk> customChunk(List<Document> source, ChunkingConfig config) {
    List<ParsedChunk> result = new ArrayList<>();
    for (Document document : source) {
      for (var chunk : customChunking.split(document.getText(), config)) {
        result.add(new ParsedChunk(chunk.text(), new HashMap<>(document.getMetadata()),
            chunk.overlapCharacters()));
      }
    }
    return result;
  }
}
