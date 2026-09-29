package com.zero.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 生产环境启动时校验 JWT 签名密钥：仓库里出现过的默认值是公开的，用它签名等于任何人都能伪造登录令牌。
 * 开发环境不校验，方便本地直接启动。
 */
@Component
public class JwtSecretGuard {

  /** HS256 要求密钥至少 256 位 */
  static final int MIN_BYTES = 32;

  /** application.yml 与 docker-compose.yml 曾经使用过的默认值 */
  static final Set<String> KNOWN_DEFAULTS =
      Set.of(
          "dev-access-secret-change-in-production",
          "dev-refresh-secret-change-in-production",
          "change-this-access-secret",
          "change-this-refresh-secret");

  public JwtSecretGuard(JwtProperties jwt, AppEnvProperties env) {
    if ("production".equalsIgnoreCase(env.env())) {
      check("JWT_ACCESS_SECRET", jwt.accessSecret());
      check("JWT_REFRESH_SECRET", jwt.refreshSecret());
    }
  }

  static void check(String name, String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(name + " 未设置，请在 .env 中配置随机长字符串（例如 openssl rand -base64 48）");
    }
    if (KNOWN_DEFAULTS.contains(secret)) {
      throw new IllegalStateException(name + " 仍是仓库中的公开默认值，请在 .env 中改为随机长字符串");
    }
    if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES) {
      throw new IllegalStateException(name + " 太短，至少需要 " + MIN_BYTES + " 字节");
    }
  }
}
