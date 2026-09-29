package com.zero.domain;

/**
 * 按客户端汇总后的授权：刷新令牌每次轮换都会新增一行，管理页只关心客户端维度。
 *
 * @param authorizedAt 该客户端最早一次授权的时间
 * @param lastUsedAt 所有令牌中最近一次使用的时间，可能为 null
 * @param expiresAt 有效时为最晚到期的有效令牌，失效时为最晚到期的令牌
 * @param active 是否还有未撤销且未过期的令牌
 */
public record OAuthClientAuthorization(
    String clientId,
    String clientName,
    String scope,
    String authorizedAt,
    String lastUsedAt,
    String expiresAt,
    boolean active) {}
