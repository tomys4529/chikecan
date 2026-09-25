package com.chikecan.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.chikecan.backend.dto.TicketAssigneeUpdateRequest;
import com.chikecan.backend.dto.TicketCreateRequest;
import com.chikecan.backend.dto.TicketResponse;
import com.chikecan.backend.dto.TicketStatusUpdateRequest;
import com.chikecan.backend.dto.TicketUpdateRequest;
import com.chikecan.backend.security.AppUserDetails;
import com.chikecan.backend.service.TicketService;

import jakarta.validation.Valid;

@RestController
public class TicketController {

  private final TicketService ticketService;

  public TicketController(TicketService ticketService) {
    this.ticketService = ticketService;
  }

  @PreAuthorize("hasRole('USER')")
  @PostMapping("/api/tickets")
  public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request,
      @AuthenticationPrincipal AppUserDetails principal) {
    TicketResponse response = ticketService.create(request, principal);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/api/tickets")
  public List<TicketResponse> list(@AuthenticationPrincipal AppUserDetails principal) {
    return ticketService.list(principal);
  }

  @GetMapping("/api/tickets/{id}")
  public TicketResponse detail(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
    return ticketService.getDetail(id, principal);
  }

  @PreAuthorize("hasRole('USER')")
  @PatchMapping("/api/tickets/{id}")
  public TicketResponse update(@PathVariable Long id, @Valid @RequestBody TicketUpdateRequest request,
      @AuthenticationPrincipal AppUserDetails principal) {
    return ticketService.updateContent(id, request, principal);
  }

  @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
  @PatchMapping("/api/tickets/{id}/status")
  public TicketResponse updateStatus(@PathVariable Long id, @Valid @RequestBody TicketStatusUpdateRequest request,
      @AuthenticationPrincipal AppUserDetails principal) {
    return ticketService.updateStatus(id, request, principal);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/api/tickets/{id}/assignee")
  public TicketResponse updateAssignee(@PathVariable Long id, @Valid @RequestBody TicketAssigneeUpdateRequest request,
      @AuthenticationPrincipal AppUserDetails principal) {
    return ticketService.updateAssignee(id, request, principal);
  }
}
