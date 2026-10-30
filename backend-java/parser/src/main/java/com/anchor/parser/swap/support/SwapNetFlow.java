package com.anchor.parser.swap.support;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainTransaction;
import com.anchor.common.evm.generated.Erc20;
import com.anchor.common.evm.generated.Weth9;
import com.anchor.common.support.AbiOutputs;
import com.anchor.common.support.Addresses;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 以 Transfer / WETH Withdrawal / tx.value 计算监控地址的净流入流出。
 */
public final class SwapNetFlow {

    private static final String TRANSFER_TOPIC = EventEncoder.encode(Erc20.TRANSFER_EVENT);
    private static final String WITHDRAWAL_TOPIC = EventEncoder.encode(Weth9.WITHDRAWAL_EVENT);
    /** ERC20 Transfer 为 topic0 + from + to；ERC721 多一个 tokenId topic，不计入。 */
    private static final int ERC20_TRANSFER_TOPICS = 3;
    private static final int WITHDRAWAL_MIN_TOPICS = 2;
    private static final int TOPIC_ADDRESS_HEX_CHARS = 40;

    private SwapNetFlow() {
    }

    /** 付出为负，收到为正。 */
    public static Map<String, BigInteger> of(ChainDefinition chain, ChainTransaction tx, ChainReceipt receipt, String user) {
        Map<String, BigInteger> delta = new HashMap<>();
        for (ChainLog logItem : receipt.logs()) {
            applyTransfer(delta, logItem, user);
            applyWithdrawal(delta, chain, tx, logItem, user);
        }
        if (tx.value() != null && tx.value().signum() > 0) {
            delta.merge(chain.nativeToken().address(), tx.value().negate(), BigInteger::add);
        }
        return delta;
    }

    private static void applyTransfer(Map<String, BigInteger> delta, ChainLog logItem, String user) {
        if (logItem.topics().size() != ERC20_TRANSFER_TOPICS || !TRANSFER_TOPIC.equalsIgnoreCase(logItem.topics().get(0))) {
            return;
        }
        String from = topicAddress(logItem.topics().get(1));
        String to = topicAddress(logItem.topics().get(2));
        BigInteger value = uint256(logItem.data());
        if (user.equals(from) && !user.equals(to)) {
            delta.merge(logItem.address(), value.negate(), BigInteger::add);
        }
        if (user.equals(to) && !user.equals(from)) {
            delta.merge(logItem.address(), value, BigInteger::add);
        }
    }

    private static void applyWithdrawal(Map<String, BigInteger> delta, ChainDefinition chain, ChainTransaction tx, ChainLog logItem, String user) {
        String weth = chain.contracts().weth();
        if (weth == null || !weth.equalsIgnoreCase(logItem.address())) {
            return;
        }
        if (logItem.topics().size() < WITHDRAWAL_MIN_TOPICS || !WITHDRAWAL_TOPIC.equalsIgnoreCase(logItem.topics().get(0))) {
            return;
        }
        String src = topicAddress(logItem.topics().get(1));
        String router = Addresses.lower(tx.to());
        if (user.equals(src) || (router != null && router.equals(src))) {
            delta.merge(chain.nativeToken().address(), uint256(logItem.data()), BigInteger::add);
        }
    }

    private static String topicAddress(String topic) {
        return Addresses.lower("0x" + topic.substring(topic.length() - TOPIC_ADDRESS_HEX_CHARS));
    }

    private static BigInteger uint256(String data) {
        @SuppressWarnings("rawtypes")
        List<Type> decoded = FunctionReturnDecoder.decode(data, AbiOutputs.single(new TypeReference<Uint256>() {}));
        if (decoded.isEmpty()) {
            return BigInteger.ZERO;
        }
        return ((Uint256) decoded.get(0)).getValue();
    }
}
