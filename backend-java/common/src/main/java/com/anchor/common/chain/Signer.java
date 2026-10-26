package com.anchor.common.chain;

import com.anchor.common.domain.SignModeEnum;

public interface Signer {
    /** 模式 A 不在后端签名。模式 B 仅允许链注册表声明了 HOT_WALLET 的测试网。 */
    String sign(TxBuilder.UnsignedTransaction transaction, SignModeEnum mode);
}
