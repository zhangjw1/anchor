package com.anchor.parser.swap.support;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainTransaction;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.domain.GasModelEnum;
import com.anchor.common.domain.SignModeEnum;
import com.anchor.common.evm.generated.Erc20;
import com.anchor.common.evm.generated.Weth9;
import org.junit.jupiter.api.Test;
import org.web3j.abi.EventEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwapNetFlowTest {

    private static final String USER = "0x1111111111111111111111111111111111111111";
    private static final String OTHER = "0x2222222222222222222222222222222222222222";
    private static final String TOKEN = "0x3333333333333333333333333333333333333333";
    private static final String WETH = "0x4200000000000000000000000000000000000006";
    private static final String ROUTER = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TRANSFER = EventEncoder.encode(Erc20.TRANSFER_EVENT);
    private static final String WITHDRAWAL = EventEncoder.encode(Weth9.WITHDRAWAL_EVENT);

    @Test
    void tracksErc20OutAndIn() {
        ChainLog out = transfer(TOKEN, USER, OTHER, BigInteger.valueOf(100), 0);
        ChainLog in = transfer(TOKEN, OTHER, USER, BigInteger.valueOf(40), 1);
        Map<String, BigInteger> delta = SwapNetFlow.of(chain(), tx(BigInteger.ZERO),
                new ChainReceipt("0xhash", true, BigInteger.TEN, List.of(out, in)), USER);
        assertEquals(BigInteger.valueOf(-60), delta.get(TOKEN));
    }

    @Test
    void countsNativeValueAsOutflow() {
        Map<String, BigInteger> delta = SwapNetFlow.of(chain(), tx(BigInteger.valueOf(5)),
                new ChainReceipt("0xhash", true, BigInteger.TEN, List.of()), USER);
        assertEquals(BigInteger.valueOf(-5), delta.get(AnchorConstants.NATIVE_TOKEN_ADDRESS));
    }

    @Test
    void countsWethWithdrawalAsNativeIn() {
        String amount = Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(7), 64);
        ChainLog withdrawal = new ChainLog(WETH, List.of(WITHDRAWAL, topic(ROUTER)), amount, 0);
        Map<String, BigInteger> delta = SwapNetFlow.of(chain(), tx(BigInteger.ZERO),
                new ChainReceipt("0xhash", true, BigInteger.TEN, List.of(withdrawal)), USER);
        assertEquals(BigInteger.valueOf(7), delta.get(AnchorConstants.NATIVE_TOKEN_ADDRESS));
    }

    @Test
    void skipsErc721StyleTransferWithExtraTopic() {
        ChainLog nft = new ChainLog(TOKEN, List.of(TRANSFER, topic(USER), topic(OTHER), topic(OTHER)),
                Numeric.toHexStringWithPrefixZeroPadded(BigInteger.ONE, 64), 0);
        Map<String, BigInteger> delta = SwapNetFlow.of(chain(), tx(BigInteger.ZERO),
                new ChainReceipt("0xhash", true, BigInteger.TEN, List.of(nft)), USER);
        assertTrue(delta.isEmpty());
    }

    private static ChainLog transfer(String token, String from, String to, BigInteger amount, int index) {
        return new ChainLog(token, List.of(TRANSFER, topic(from), topic(to)),
                Numeric.toHexStringWithPrefixZeroPadded(amount, 64), index);
    }

    private static String topic(String address) {
        return "0x" + "0".repeat(24) + address.substring(2).toLowerCase();
    }

    private static ChainTransaction tx(BigInteger value) {
        return new ChainTransaction("0xhash", USER, ROUTER, value, "0x");
    }

    private static ChainDefinition chain() {
        return new ChainDefinition(
                "base", 8453L, "Base", ChainTypeEnum.EVM, false, 10,
                GasModelEnum.EIP1559_OP_L1_FEE, List.of(SignModeEnum.CLIENT),
                new ChainDefinition.NativeToken("ETH", 18, AnchorConstants.NATIVE_TOKEN_ADDRESS),
                List.of(),
                new ChainDefinition.Contracts(WETH, null, null, null, null, null, null, null, null, null, null, null, null),
                Set.of(), List.of());
    }
}
