package com.chikecan.backend.dto;

/**
 * PATCH /api/tickets/{id}/status 専用のレスポンス。
 * 通常のチケット取得系APIが返すTicketResponseへ一時的なXP演出情報を
 * 混在させないよう、この更新1回に限定した専用DTOとして分離している。
 */
public class TicketStatusUpdateResponse {

  private final TicketResponse ticket;
  private final XpAwardResult xpResult;

  public TicketStatusUpdateResponse(TicketResponse ticket, XpAwardResult xpResult) {
    this.ticket = ticket;
    this.xpResult = xpResult;
  }

  public TicketResponse getTicket() {
    return ticket;
  }

  public XpAwardResult getXpResult() {
    return xpResult;
  }
}
