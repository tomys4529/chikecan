package com.chikecan.backend.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chikecan.backend.dto.AgentSummaryResponse;
import com.chikecan.backend.service.UserService;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

  private final UserService userService;

  public AdminUserController(UserService userService) {
    this.userService = userService;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/agents")
  public List<AgentSummaryResponse> listAgents() {
    return userService.listAgents();
  }
}
