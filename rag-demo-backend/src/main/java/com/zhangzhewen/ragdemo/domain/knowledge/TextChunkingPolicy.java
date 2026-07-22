package com.zhangzhewen.ragdemo.domain.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义文本切片规则：优先使用指定分隔符，超长时回退到句末标点和硬切。
 */
public class TextChunkingPolicy {

  private static final List<String> FALLBACK_SEPARATORS =
      List.of("。", "！", "？", "；", ".", "!", "?", ";");

  /**
   * 生成长度受限且带相邻重叠的文本块。
   */
  public List<TextChunk> split(String source, ChunkingConfig config) {
    if (source == null || source.isBlank()) {
      return List.of();
    }
    String normalizedLineEndings = source.replace("\r\n", "\n").replace('\r', '\n');
    int newContentBudget = config.maxChunkLength() - config.overlapLength();
    List<String> atoms = new ArrayList<>();
    for (String primary : splitAfter(normalizedLineEndings, config.separator())) {
      String cleaned = clean(primary, config.normalizeWhitespace());
      if (!cleaned.isBlank()) {
        atoms.addAll(splitOversized(cleaned, newContentBudget));
      }
    }
    List<String> bases = pack(atoms, newContentBudget);
    List<TextChunk> result = new ArrayList<>();
    String previous = "";
    for (String base : bases) {
      if (previous.isEmpty() || config.overlapLength() == 0) {
        result.add(new TextChunk(base, 0));
        previous = base;
        continue;
      }
      String overlap = tail(previous, config.overlapLength());
      String separator = needsSpace(overlap, base)
          && overlap.length() + base.length() < config.maxChunkLength() ? " " : "";
      String text = overlap + separator + base;
      result.add(new TextChunk(text, overlap.length()));
      previous = text;
    }
    return List.copyOf(result);
  }

  private List<String> splitOversized(String text, int limit) {
    if (text.length() <= limit) {
      return List.of(text);
    }
    for (String separator : FALLBACK_SEPARATORS) {
      List<String> parts = splitAfter(text, separator).stream().map(String::trim)
          .filter(part -> !part.isEmpty()).toList();
      if (parts.size() > 1) {
        List<String> result = new ArrayList<>();
        for (String part : parts) {
          result.addAll(splitOversized(part, limit));
        }
        return result;
      }
    }
    List<String> result = new ArrayList<>();
    for (int start = 0; start < text.length(); start += limit) {
      result.add(text.substring(start, Math.min(text.length(), start + limit)));
    }
    return result;
  }

  private List<String> pack(List<String> atoms, int limit) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String atom : atoms) {
      String joiner = current.isEmpty() || !needsSpace(current.toString(), atom) ? "" : " ";
      if (!current.isEmpty() && current.length() + joiner.length() + atom.length() > limit) {
        result.add(current.toString());
        current.setLength(0);
        joiner = "";
      }
      current.append(joiner).append(atom);
    }
    if (!current.isEmpty()) {
      result.add(current.toString());
    }
    return result;
  }

  private List<String> splitAfter(String text, String separator) {
    if (separator == null || separator.isEmpty()) {
      return List.of(text);
    }
    List<String> result = new ArrayList<>();
    int start = 0;
    int found;
    while ((found = text.indexOf(separator, start)) >= 0) {
      int end = found + separator.length();
      result.add(text.substring(start, end));
      start = end;
    }
    if (start < text.length()) {
      result.add(text.substring(start));
    }
    return result.isEmpty() ? List.of(text) : result;
  }

  private String clean(String text, boolean normalizeWhitespace) {
    return normalizeWhitespace ? text.replaceAll("[\\s\\u00a0]+", " ").trim() : text.trim();
  }

  private String tail(String text, int length) {
    return text.substring(Math.max(0, text.length() - length));
  }

  private boolean needsSpace(String left, String right) {
    return !left.isEmpty() && !right.isEmpty()
        && !Character.isWhitespace(left.charAt(left.length() - 1))
        && !Character.isWhitespace(right.charAt(0));
  }

  /**
   * 切片文本及其中来自上一块的重叠字符数。
   */
  public record TextChunk(String text, int overlapCharacters) {

  }
}
