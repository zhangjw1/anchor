package com.anchor.common.support;

import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 从 RPC 异常信息或 revert data 中尽量解出可读原因。
 */
public final class RevertReasons {

    /** Error(string) 选择器。 */
    private static final String ERROR_STRING_SELECTOR = "0x08c379a0";

    private RevertReasons() {
    }

    /**
     * 优先解码 Error(string)；否则返回清洗后的原始消息。
     */
    public static String explain(Throwable error) {
        if (error == null) {
            return "execution reverted";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "execution reverted";
        }
        String decoded = decodeErrorString(message);
        return decoded != null ? decoded : message.trim();
    }

    /**
     * 从含 0x08c379a0… 的文本中解出 Error(string) 参数。
     */
    public static String decodeErrorString(String raw) {
        if (raw == null) {
            return null;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(ERROR_STRING_SELECTOR);
        if (idx < 0) {
            return null;
        }
        String hex = lower.substring(idx);
        int end = hex.length();
        for (int i = 2; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                end = i;
                break;
            }
        }
        hex = hex.substring(0, end);
        if (hex.length() < 2 + 8 + 64 + 64) {
            return null;
        }
        try {
            // selector(4) + offset(32) + length(32) + data
            String lengthHex = hex.substring(2 + 8 + 64, 2 + 8 + 64 + 64);
            int length = Integer.parseInt(lengthHex, 16);
            if (length < 0 || length > 1024) {
                return null;
            }
            int dataStart = 2 + 8 + 64 + 64;
            int dataChars = length * 2;
            if (hex.length() < dataStart + dataChars) {
                return null;
            }
            byte[] bytes = Numeric.hexStringToByteArray("0x" + hex.substring(dataStart, dataStart + dataChars));
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? null : text;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
