package com.anchor.common.manager;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.Signer;
import com.anchor.common.chain.TxBuilder;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.SignModeEnum;

/**
 * 模式 A：不在后端签名，仅校验链允许 CLIENT。
 */
public class ClientSigner implements Signer {

    private final ChainRegistry chainRegistry;

    public ClientSigner(ChainRegistry chainRegistry) {
        this.chainRegistry = chainRegistry;
    }

    @Override
    public String sign(TxBuilder.UnsignedTransaction transaction, SignModeEnum mode) {
        if (mode != SignModeEnum.CLIENT) {
            throw new AnchorException("UNSUPPORTED", "ClientSigner 仅支持 CLIENT 模式");
        }
        ChainDefinition chain = chainRegistry.requireById(transaction.chainId());
        if (chain.signModes() == null || !chain.signModes().contains(SignModeEnum.CLIENT)) {
            throw new AnchorException("UNSUPPORTED", "当前链不允许 CLIENT 签名");
        }
        // 模式 A：返回空串表示由前端钱包签名
        return "";
    }
}
