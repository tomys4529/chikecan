package com.chikecan.backend.dto;

import java.util.List;

/**
 * Spring Dataの{@code Page}をそのままJSONへ公開せず、安定した形へ変換するための
 * 汎用レスポンスDTO。チケット一覧専用にせず汎用化しているのは、将来的に
 * ユーザー一覧など他の一覧APIでもページングが必要になった場合に再利用するため。
 */
public class PageResponse<T> {

  private final List<T> content;
  private final int page;
  private final int size;
  private final long totalElements;
  private final int totalPages;
  private final boolean first;
  private final boolean last;

  public PageResponse(List<T> content, int page, int size, long totalElements, int totalPages,
      boolean first, boolean last) {
    this.content = content;
    this.page = page;
    this.size = size;
    this.totalElements = totalElements;
    this.totalPages = totalPages;
    this.first = first;
    this.last = last;
  }

  public List<T> getContent() {
    return content;
  }

  public int getPage() {
    return page;
  }

  public int getSize() {
    return size;
  }

  public long getTotalElements() {
    return totalElements;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public boolean isFirst() {
    return first;
  }

  public boolean isLast() {
    return last;
  }
}
