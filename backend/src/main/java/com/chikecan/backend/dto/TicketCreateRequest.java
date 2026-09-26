package com.chikecan.backend.dto;

import com.chikecan.backend.entity.TicketPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TicketCreateRequest {

  @NotBlank(message = "タイトルは必須です")
  @Size(max = 50, message = "タイトルは50文字以内で入力してください")
  private String title;

  @NotBlank(message = "内容は必須です")
  @Size(max = 500, message = "内容は500文字以内で入力してください")
  private String description;

  @NotNull(message = "優先度は必須です")
  private TicketPriority priority;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public TicketPriority getPriority() {
    return priority;
  }

  public void setPriority(TicketPriority priority) {
    this.priority = priority;
  }
}
