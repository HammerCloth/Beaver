package com.zero.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zero.domain.OAuthAuthorizationView;
import com.zero.domain.OAuthClientAuthorization;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OAuthServiceGroupingTest {

  private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

  private static OAuthAuthorizationView token(
      String clientId, String name, String createdAt, String lastUsedAt, String expiresAt, String revokedAt) {
    OAuthAuthorizationView v = new OAuthAuthorizationView();
    v.setId(clientId + createdAt);
    v.setClientId(clientId);
    v.setClientName(name);
    v.setScope("mcp:read");
    v.setCreatedAt(createdAt);
    v.setLastUsedAt(lastUsedAt);
    v.setExpiresAt(expiresAt);
    v.setRevokedAt(revokedAt);
    return v;
  }

  /** 刷新令牌轮换后同一客户端有多行：合并为一行，时间取最早授权 / 最近使用 / 有效令牌的到期 */
  @Test
  void mergesRotatedTokensOfOneClient() {
    List<OAuthClientAuthorization> out =
        OAuthService.groupByClient(
            List.of(
                token("mcp_a", "Claude", "2026-09-29 09:00:00", null, "2026-10-29T09:00:00Z", null),
                token("mcp_a", "Claude", "2026-09-29 08:00:00", "2026-09-29 09:00:00", "2026-10-29T08:00:00Z", "2026-09-29 09:00:00"),
                token("mcp_a", "Claude", "2026-09-20 08:00:00", "2026-09-29 08:00:00", "2026-10-20T08:00:00Z", "2026-09-29 08:00:00")),
            NOW);

    assertEquals(1, out.size());
    OAuthClientAuthorization c = out.get(0);
    assertEquals("mcp_a", c.clientId());
    assertEquals("2026-09-20 08:00:00", c.authorizedAt());
    assertEquals("2026-09-29 09:00:00", c.lastUsedAt());
    assertEquals("2026-10-29T09:00:00Z", c.expiresAt());
    assertTrue(c.active());
  }

  @Test
  void clientWithOnlyRevokedOrExpiredTokensIsInactiveAndSortedLast() {
    List<OAuthClientAuthorization> out =
        OAuthService.groupByClient(
            List.of(
                token("mcp_old", "Claude", "2026-09-01 08:00:00", "2026-09-28 08:00:00", "2026-10-01T08:00:00Z", "2026-09-28 08:00:00"),
                token("mcp_exp", "Cursor", "2026-07-01 08:00:00", null, "2026-08-01T08:00:00Z", null),
                token("mcp_new", "Claude", "2026-09-28 08:00:00", null, "2026-10-28T08:00:00Z", null)),
            NOW);

    assertEquals(List.of("mcp_new", "mcp_old", "mcp_exp"), out.stream().map(OAuthClientAuthorization::clientId).toList());
    assertTrue(out.get(0).active());
    assertFalse(out.get(1).active());
    assertFalse(out.get(2).active());
    assertNull(out.get(2).lastUsedAt());
  }
}
