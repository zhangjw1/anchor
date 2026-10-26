package com.anchor.common.domain;

public class AnchorException extends RuntimeException {

    private final String code;

    public AnchorException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
