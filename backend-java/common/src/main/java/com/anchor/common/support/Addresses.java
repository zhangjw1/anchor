package com.anchor.common.support;

import com.anchor.common.domain.AnchorException;

import java.util.regex.Pattern;

/**
 * EVM 地址校验和大小写规范。
 */
public final class Addresses {

    private static final Pattern EVM = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private Addresses() {
    }
    /**
     * 必须是 0x 加 40 位十六进制，结果转成小写。
     */
    public static String normalizeEvm(String raw) {
        if (raw == null || !EVM.matcher(raw.trim()).matches()) {
            throw new AnchorException("INVALID_ADDRESS", "地址格式不正确");
        }
        return raw.trim().toLowerCase();
    }
    /**
     * 只转小写，不校验格式。空值保持空。
     */
    public static String lower(String raw) {
        return raw == null ? null : raw.toLowerCase();
    }
}
