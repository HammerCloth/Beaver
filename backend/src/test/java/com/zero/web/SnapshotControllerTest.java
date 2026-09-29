package com.zero.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zero.security.JwtPrincipal;
import com.zero.service.SnapshotService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SnapshotControllerTest {

  private static final String USER_ID = "u1";

  private SnapshotService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(SnapshotService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new SnapshotController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(new JwtPrincipal(USER_ID, false), null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getAcceptsUuidHexId() throws Exception {
    String id = "0123456789abcdef0123456789abcdef";
    when(service.getDetail(USER_ID, id)).thenReturn(Map.of("id", id));

    mvc.perform(get("/api/v1/snapshots/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.snapshot.id").value(id));
  }

  /** scripts/seed_demo_data.py 生成的 id 形如 demo- 加 24 位 hex，曾因路由正则不匹配返回 500 */
  @Test
  void getAcceptsDemoSeedId() throws Exception {
    String id = "demo-ff00ca3d1075450c83546b61";
    when(service.getDetail(USER_ID, id)).thenReturn(Map.of("id", id));

    mvc.perform(get("/api/v1/snapshots/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.snapshot.id").value(id));
  }

  @Test
  void literalLatestRouteStillWinsOverIdPattern() throws Exception {
    when(service.getLatestOrNull(USER_ID)).thenReturn(Map.of("snapshot", Map.of("id", "x")));

    mvc.perform(get("/api/v1/snapshots/latest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.snapshot.id").value("x"));
  }

  @Test
  void unmatchedRouteReturns404InsteadOf500() throws Exception {
    mvc.perform(get("/api/v1/snapshots/a/b")).andExpect(status().isNotFound());
  }
}
