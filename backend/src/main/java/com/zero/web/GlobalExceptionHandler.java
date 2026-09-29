package com.zero.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
    String msg = ex.getReason() != null ? ex.getReason() : "请求错误";
    return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", msg));
  }

  /** 请求体无法解析，例如不支持的币种 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "参数不合法"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleAny(Exception ex) {
    // Spring MVC 自身的异常（路由不存在、方法不支持等）自带状态码，不能一律当成 500
    if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
      return ResponseEntity.status(er.getStatusCode()).body(Map.of("error", "请求错误"));
    }
    log.error("未处理的异常", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "服务器内部错误"));
  }
}
