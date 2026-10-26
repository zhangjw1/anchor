package com.anchor.common.support;

import com.anchor.common.domain.AnchorException;

import java.util.regex.Pattern;

public final class Addresses {

    private static final Pattern EVM = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private Addresses() {
    }

    public static String normalizeEvm(String raw) {
        if (raw == null || !EVM.matcher(raw.trim()).matches()) {
            throw new AnchorException("INVALID_ADDRESS", "地址格式不正确");
        }
        return raw.trim().toLowerCase();
    }

    public static String lower(String raw) {
        return raw == null ? null : raw.toLowerCase();
    }
}
