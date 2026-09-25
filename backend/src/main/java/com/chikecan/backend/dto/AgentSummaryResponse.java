package com.chikecan.backend.dto;

import com.chikecan.backend.entity.User;

public class AgentSummaryResponse {

  private final Long id;
  private final String name;

  public AgentSummaryResponse(User user) {
    this.id = user.getId();
    this.name = user.getName();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
