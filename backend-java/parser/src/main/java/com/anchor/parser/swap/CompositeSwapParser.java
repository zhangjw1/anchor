package com.anchor.parser.swap;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.support.Addresses;
import com.anchor.parser.swap.hop.DetectedHop;
import com.anchor.parser.swap.hop.DexHopParser;
import com.anchor.parser.swap.support.AggregatorLabels;
import com.anchor.parser.swap.support.SwapDirections;
import com.anchor.parser.swap.support.SwapNetFlow;
import com.anchor.parser.swap.support.SwapPathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 汇总各协议跳，按用户净流入流出确定兑换两端，再只保留两端之间路径上的跳。
 */
@Service
@Primary
public class CompositeSwapParser implements SwapParser {

    private static final Logger log = LoggerFactory.getLogger(CompositeSwapParser.class);

    private final List<DexHopParser> hopParsers;

    public CompositeSwapParser(List<DexHopParser> hopParsers) {
        this.hopParsers = List.copyOf(hopParsers);
    }

    @Override
    public Optional<ParsedSwap> parse(SwapParseContext context) {
        String user = Addresses.lower(context.transaction().from());
        if (!context.receipt().success() || user == null) {
            return Optional.empty();
        }
        List<DetectedHop> detected = detectAll(context);
        if (detected.isEmpty()) {
            return Optional.empty();
        }
        Optional<NetFlow> flow = uniqueNetFlow(context, user);
        if (flow.isEmpty()) {
            log.info("净流入流出不唯一，跳过该交换，chainId={}，交易={}", context.chain().chainId(), context.transaction().hash());
            return Optional.empty();
        }
        ChainDefinition chain = context.chain();
        // 处理交易的hop，例如：用户付出币A，收到币B，使用1inch交易，交易中有A->C->B的跳，至少有A->C和C->B两个跳，path里就是至少两条数据。
        List<DetectedHop> path = SwapPathResolver.onPath(detected, poolToken(chain, flow.get().tokenIn()), poolToken(chain, flow.get().tokenOut()));
        if (path.isEmpty()) {
            log.info("没有连通付出币与收到币的跳，跳过该交换，chainId={}，交易={}", chain.chainId(), context.transaction().hash());
            return Optional.empty();
        }
        return Optional.of(toParsedSwap(context, user, flow.get(), toParsedHops(path)));
    }

    private List<DetectedHop> detectAll(SwapParseContext context) {
        List<DetectedHop> detected = new ArrayList<>();
        for (DexHopParser hopParser : hopParsers) {
            detected.addAll(hopParser.detect(context));
        }
        detected.sort(Comparator.comparingInt(DetectedHop::logIndex));
        return detected;
    }

    /** 用户恰好付出一种币、收到一种币时返回，否则为空。 */
    private static Optional<NetFlow> uniqueNetFlow(SwapParseContext context, String user) {
        Map<String, BigInteger> delta = SwapNetFlow.of(context.chain(), context.transaction(), context.receipt(), user);
        List<Map.Entry<String, BigInteger>> inputs = delta.entrySet().stream().filter(e -> e.getValue().signum() < 0).toList();
        List<Map.Entry<String, BigInteger>> outputs = delta.entrySet().stream().filter(e -> e.getValue().signum() > 0).toList();
        if (inputs.size() != 1 || outputs.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(new NetFlow(Addresses.lower(inputs.get(0).getKey()), inputs.get(0).getValue().abs(),
                Addresses.lower(outputs.get(0).getKey()), outputs.get(0).getValue()));
    }

    /** 池子里没有原生币，原生币按 WETH 参与路径匹配。 */
    private static String poolToken(ChainDefinition chain, String token) {
        if (token.equalsIgnoreCase(chain.nativeToken().address()) && chain.contracts().weth() != null) {
            return Addresses.lower(chain.contracts().weth());
        }
        return token;
    }

    private static List<ParsedHop> toParsedHops(List<DetectedHop> path) {
        List<ParsedHop> hops = new ArrayList<>(path.size());
        for (int i = 0; i < path.size(); i++) {
            DetectedHop hop = path.get(i);
            hops.add(new ParsedHop(i, hop.dex(), hop.pool(), hop.tokenIn(), hop.tokenOut(), hop.amountIn(), hop.amountOut(), hop.logIndex()));
        }
        return List.copyOf(hops);
    }

    private static ParsedSwap toParsedSwap(SwapParseContext context, String user, NetFlow flow, List<ParsedHop> hops) {
        ChainDefinition chain = context.chain();
        return new ParsedSwap(
                chain.chainId(),
                context.transaction().hash(),
                hops.get(0).logIndex(),
                context.block().number(),
                context.block().hash(),
                context.block().timestamp(),
                user,
                Addresses.lower(context.transaction().to()),
                resolveDex(hops),
                SwapDirections.of(chain, flow.tokenIn(), flow.tokenOut()),
                flow.tokenIn(),
                flow.tokenOut(),
                flow.amountIn(),
                flow.amountOut(),
                user,
                AggregatorLabels.of(chain, context.transaction().to()),
                hops);
    }

    /** 全部跳同属一个协议时用该协议名，否则记为 Multi。 */
    private static String resolveDex(List<ParsedHop> hops) {
        long distinct = hops.stream().map(ParsedHop::dex).distinct().count();
        return distinct == 1 ? hops.get(0).dex() : AnchorConstants.DEX_MULTI;
    }

    private record NetFlow(String tokenIn, BigInteger amountIn, String tokenOut, BigInteger amountOut) {
    }
}
