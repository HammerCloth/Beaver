package com.zero.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtSecretGuardTest {

  private static final String STRONG_A = "k3Jm9Qp2Vx7Lr5Tz8Nw1Hc4Bf6Gd0Sy2Ea7Ui9Ko";
  private static final String STRONG_B = "Pq8Zx1Mn4Bv7Cr2Lt5Hy9Jd3Fs6Gw0Ka8Ue1Io4Rp";

  private static JwtProperties jwt(String access, String refresh) {
    return new JwtProperties(access, refresh, 3600, 86400);
  }

  @Test
  void developmentAllowsDefaults() {
    assertDoesNotThrow(
        () ->
            new JwtSecretGuard(
                jwt("dev-access-secret-change-in-production", "dev-refresh-secret-change-in-production"),
                new AppEnvProperties("development")));
  }

  @Test
  void productionRejectsPublicDefaults() {
    AppEnvProperties prod = new AppEnvProperties("production");
    assertThrows(IllegalStateException.class, () -> new JwtSecretGuard(jwt("change-this-access-secret", STRONG_B), prod));
    assertThrows(
        IllegalStateException.class,
        () -> new JwtSecretGuard(jwt(STRONG_A, "dev-refresh-secret-change-in-production"), prod));
  }

  @Test
  void productionRejectsMissingOrShortSecrets() {
    AppEnvProperties prod = new AppEnvProperties("production");
    assertThrows(IllegalStateException.class, () -> new JwtSecretGuard(jwt("", STRONG_B), prod));
    assertThrows(IllegalStateException.class, () -> new JwtSecretGuard(jwt("short-secret", STRONG_B), prod));
  }

  @Test
  void productionAcceptsStrongDistinctSecrets() {
    assertDoesNotThrow(() -> new JwtSecretGuard(jwt(STRONG_A, STRONG_B), new AppEnvProperties("production")));
  }
}
