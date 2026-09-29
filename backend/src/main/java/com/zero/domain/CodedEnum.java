package com.zero.domain;

/** 以固定整数编码持久化的枚举，编码一经发布不可修改 */
public interface CodedEnum {
  int code();
}
