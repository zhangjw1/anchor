package com.anchor.parser.swap;

import com.anchor.common.chain.ChainLog;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainTransaction;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.evm.generated.Erc20;
import com.anchor.common.evm.generated.UniswapV3Pool;
import com.anchor.common.evm.generated.Weth9;
import com.anchor.common.support.Addresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UniswapV3SwapParser implements SwapParser {

    private static final Logger log = LoggerFactory.getLogger(UniswapV3SwapParser.class);
    private static final String SWAP_TOPIC = EventEncoder.encode(UniswapV3Pool.SWAP_EVENT);
    private static final String TRANSFER_TOPIC = EventEncoder.encode(Erc20.TRANSFER_EVENT);
    private static final String WITHDRAWAL_TOPIC = EventEncoder.encode(Weth9.WITHDRAWAL_EVENT);

    private final PoolFactoryLookup poolFactoryLookup;

    public UniswapV3SwapParser(PoolFactoryLookup poolFactoryLookup) {
        this.poolFactoryLookup = poolFactoryLookup;
    }

    @Override
    public Optional<ParsedSwap> parse(SwapParseContext context) {
        String factory = context.chain().contracts().uniswapV3Factory();
        String user = Addresses.lower(context.transaction().from());
        if (factory == null || user == null) {
            return Optional.empty();
        }
        List<ChainLog> verified = verifiedSwaps(context, factory);
        if (verified.size() != 1) {
            return Optional.empty();
        }
        Map<String, BigInteger> delta = netFlow(context.chain(), context.transaction(), context.receipt(), user);
        List<Map.Entry<String, BigInteger>> inputs = new ArrayList<>();
        List<Map.Entry<String, BigInteger>> outputs = new ArrayList<>();
        for (Map.Entry<String, BigInteger> entry : delta.entrySet()) {
            if (entry.getValue().signum() < 0) {
                inputs.add(entry);
            } else if (entry.getValue().signum() > 0) {
                outputs.add(entry);
            }
        }
        if (inputs.size() != 1 || outputs.size() != 1) {
            log.info("skip swap with mixed net flow chainId={} txHash={}", context.chain().chainId(), context.transaction().hash());
            return Optional.empty();
        }
        String tokenIn = inputs.get(0).getKey();
        String tokenOut = outputs.get(0).getKey();
        ChainTransaction tx = context.transaction();
        return Optional.of(new ParsedSwap(
                context.chain().chainId(),
                tx.hash(),
                verified.get(0).logIndex(),
                context.block().number(),
                context.block().hash(),
                context.block().timestamp(),
                user,
                Addresses.lower(tx.to()),
                AnchorConstants.DEX_UNISWAP_V3,
                SwapDirections.of(context.chain(), tokenIn, tokenOut),
                tokenIn,
                tokenOut,
                inputs.get(0).getValue().abs(),
                outputs.get(0).getValue(),
                user));
    }

    private List<ChainLog> verifiedSwaps(SwapParseContext context, String factory) {
        List<ChainLog> verified = new ArrayList<>();
        for (ChainLog logItem : context.receipt().logs()) {
            if (logItem.topics().isEmpty() || !SWAP_TOPIC.equalsIgnoreCase(logItem.topics().get(0))) {
                continue;
            }
            String poolFactory = poolFactoryLookup.findFactory(context.chain().chainId(), logItem.address()).orElse(null);
            if (factory.equalsIgnoreCase(poolFactory)) {
                verified.add(logItem);
            }
        }
        return verified;
    }

    /** 付出是监控地址转出的 ERC20 加上 tx.value；收到是转入的 ERC20，以及用户或 Router 拆出的 ETH。 */
    private Map<String, BigInteger> netFlow(com.anchor.common.chain.ChainDefinition chain, ChainTransaction tx, ChainReceipt receipt, String user) {
        Map<String, BigInteger> delta = new HashMap<>();
        for (ChainLog logItem : receipt.logs()) {
            applyTransfer(delta, logItem, user);
            applyWithdrawal(delta, chain, tx, logItem, user);
        }
        if (tx.value() != null && tx.value().signum() > 0) {
            add(delta, chain.nativeToken().address(), tx.value().negate());
        }
        return delta;
    }

    private static void applyTransfer(Map<String, BigInteger> delta, ChainLog logItem, String user) {
        if (logItem.topics().size() < 3 || !TRANSFER_TOPIC.equalsIgnoreCase(logItem.topics().get(0))) {
            return;
        }
        String from = topicAddress(logItem.topics().get(1));
        String to = topicAddress(logItem.topics().get(2));
        BigInteger value = uint256(logItem.data());
        if (user.equals(from) && !user.equals(to)) {
            add(delta, logItem.address(), value.negate());
        }
        if (user.equals(to) && !user.equals(from)) {
            add(delta, logItem.address(), value);
        }
    }

    private static void applyWithdrawal(Map<String, BigInteger> delta, com.anchor.common.chain.ChainDefinition chain,
                                        ChainTransaction tx, ChainLog logItem, String user) {
        String weth = chain.contracts().weth();
        if (weth == null || !weth.equalsIgnoreCase(logItem.address())) {
            return;
        }
        if (logItem.topics().size() < 2 || !WITHDRAWAL_TOPIC.equalsIgnoreCase(logItem.topics().get(0))) {
            return;
        }
        String src = topicAddress(logItem.topics().get(1));
        String router = Addresses.lower(tx.to());
        if (user.equals(src) || (router != null && router.equals(src))) {
            add(delta, chain.nativeToken().address(), uint256(logItem.data()));
        }
    }

    private static void add(Map<String, BigInteger> delta, String token, BigInteger amount) {
        delta.merge(token, amount, BigInteger::add);
    }

    private static String topicAddress(String topic) {
        return Addresses.lower("0x" + topic.substring(topic.length() - 40));
    }

    private static BigInteger uint256(String data) {
        @SuppressWarnings("rawtypes")
        List<org.web3j.abi.datatypes.Type> decoded = FunctionReturnDecoder.decode(data, AbiOutputs.single(new TypeReference<Uint256>() {}));
        if (decoded.isEmpty()) {
            return BigInteger.ZERO;
        }
        return ((Uint256) decoded.get(0)).getValue();
    }
}
