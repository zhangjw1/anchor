package com.anchor.api.vo;
/**
 * 统一错误体，code 给程序判断，message 给界面展示。
 */
public record ErrorVO(String code, String message) {
}
