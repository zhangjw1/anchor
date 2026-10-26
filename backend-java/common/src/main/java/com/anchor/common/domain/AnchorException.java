package com.anchor.common.domain;
/**
 * 可预期的业务异常。code 决定接口返回的 HTTP 状态。
 */
public class AnchorException extends RuntimeException {

    private final String code;
    /**
     * code 稳定给调用方判断，message 是中文说明。
     */
    public AnchorException(String code, String message) {
        super(message);
        this.code = code;
    }
    /**
     * 错误码，例如 NOT_FOUND、CONFLICT。
     */
    public String getCode() {
        return code;
    }
}
