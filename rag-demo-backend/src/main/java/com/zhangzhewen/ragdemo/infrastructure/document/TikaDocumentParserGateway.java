package com.zhangzhewen.ragdemo.infrastructure.document;

import com.zhangzhewen.ragdemo.domain.gateway.DocumentParserGateway;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

/**
 * Markdown 使用 Spring AI Reader，其余格式使用 Apache Tika 3。
 */
@Component
public class TikaDocumentParserGateway implements DocumentParserGateway {

  private final Tika tika = new Tika();

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

  /**
   * 解析文本并以 TokenTextSplitter 为基础增加轻量相邻重叠。
   */
  @Override
  public List<ParsedChunk> parse(String path, String extension, int chunkSize, int overlap) {
    try {
      List<Document> source;
      if ("md".equals(extension)) {
        var config = MarkdownDocumentReaderConfig.builder().withIncludeCodeBlock(true)
            .withIncludeBlockquote(true).build();
        source = new MarkdownDocumentReader(new FileSystemResource(path), config).get();
      } else {
        source = List.of(new Document(tika.parseToString(Path.of(path))));
      }
      TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(chunkSize)
          .withMinChunkSizeChars(50).build();
      List<Document> split = splitter.apply(source);
      List<ParsedChunk> result = new ArrayList<>();
      String previous = "";
      for (Document d : split) {
        String prefix = previous.isBlank() ? "" : previous.substring(
            Math.max(0, previous.length() - Math.min(previous.length(), overlap * 3)));
        String text = prefix + d.getText();
        result.add(new ParsedChunk(text, new HashMap<>(d.getMetadata())));
        previous = d.getText();
      }
      return result;
    } catch (Exception e) {
      throw new IllegalArgumentException("文档解析失败: " + e.getMessage(), e);
    }
  }
}
