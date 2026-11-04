package com.anchor.api.service.impl;

import com.anchor.api.service.QuoteQueryService;
import com.anchor.api.service.TradeQueryService;
import com.anchor.api.service.WebSocketPushService;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.chain.Signer;
import com.anchor.common.chain.StoredQuote;
import com.anchor.common.chain.TxBuilder;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.SignModeEnum;
import com.anchor.common.metrics.AnchorMetricNames;
import com.anchor.common.support.Addresses;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * P5a 交易服务：仅 base-sepolia，模式 A。
 */
@Service
public class TradeQueryServiceImpl implements TradeQueryService {

    private static final Logger log = LoggerFactory.getLogger(TradeQueryServiceImpl.class);
    private static final Pattern TX_HASH = Pattern.compile("^0x[0-9a-fA-F]{64}$");
    private static final BigInteger MAX_UINT256 = new BigInteger("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);

    private final ChainRegistry chainRegistry;
    private final QuoteQueryService quoteQueryService;
    private final TxBuilder txBuilder;
    private final Signer signer;
    private final RpcGateway rpcGateway;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final WebSocketPushService webSocketPushService;
    private final MeterRegistry meterRegistry;
    private final ExecutorService pollExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public TradeQueryServiceImpl(ChainRegistry chainRegistry, QuoteQueryService quoteQueryService, TxBuilder txBuilder,
                                 Signer signer, RpcGateway rpcGateway, StringRedisTemplate redis, ObjectMapper objectMapper,
                                 WebSocketPushService webSocketPushService, MeterRegistry meterRegistry) {
        this.chainRegistry = chainRegistry;
        this.quoteQueryService = quoteQueryService;
        this.txBuilder = txBuilder;
        this.signer = signer;
        this.rpcGateway = rpcGateway;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.webSocketPushService = webSocketPushService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public PrepareResult prepare(String quoteId, String userAddress, boolean infiniteApprove) {
        StoredQuote quote = requireQuote(quoteId);
        requireTradeChain(quote.chainId());
        String user = Addresses.normalizeEvm(userAddress);
        ChainDefinition chain = chainRegistry.requireById(quote.chainId());
        String router = chain.contracts().swapRouter02();
        if (router == null || router.isBlank()) {
            throw new AnchorException("UNSUPPORTED", "当前链未配置 SwapRouter02");
        }

        TxBuilder.UnsignedTransaction swapTx = txBuilder.buildSwap(quote, user);
        signer.sign(swapTx, SignModeEnum.CLIENT);

        boolean needsApprove = false;
        TxBuilder.UnsignedTransaction approveTx = null;
        if (!quote.nativeIn()) {
            BigInteger allowance = readAllowance(quote.chainId(), quote.tokenIn(), user, router);
            if (allowance.compareTo(quote.amountIn()) < 0) {
                needsApprove = true;
                BigInteger amount = infiniteApprove ? MAX_UINT256 : quote.amountIn();
                approveTx = txBuilder.buildApprove(quote.chainId(), quote.tokenIn(), router, amount);
            }
        }

        BigInteger value = new BigInteger(swapTx.value());
        BigInteger gas;
        try {
            rpcGateway.ethCall(quote.chainId(), user, swapTx.to(), swapTx.data(), value);
            gas = rpcGateway.estimateGas(quote.chainId(), user, swapTx.to(), swapTx.data(), value);
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? "simulation failed" : ex.getMessage();
            throw new AnchorException("SIMULATION_FAILED", "兑换模拟失败: " + reason);
        }

        long deadline = System.currentTimeMillis() / 1_000L + AnchorConstants.TRADE_DEADLINE_SECONDS;
        return new PrepareResult(quote.quoteId(), quote.expiresAtEpochMs(), needsApprove, approveTx, swapTx, gas,
                quote.amountOutMinimum().toString(), deadline);
    }

    @Override
    public TradeStatusView report(long chainId, String txHash, String kind, String quoteId, String userAddress) {
        requireTradeChain(chainId);
        if (!TX_HASH.matcher(txHash).matches()) {
            throw new AnchorException("VALIDATION_ERROR", "交易哈希不正确");
        }
        String normalizedKind = kind == null ? "" : kind.trim().toUpperCase();
        if (!"APPROVE".equals(normalizedKind) && !"SWAP".equals(normalizedKind)) {
            throw new AnchorException("VALIDATION_ERROR", "kind 必须是 APPROVE 或 SWAP");
        }
        String hash = txHash.toLowerCase();
        String user = userAddress == null || userAddress.isBlank() ? null : Addresses.normalizeEvm(userAddress);
        TradeStatusView view = new TradeStatusView(chainId, hash, normalizedKind, "PENDING", quoteId, user, null,
                System.currentTimeMillis());
        saveStatus(view);
        meterRegistry.counter(AnchorMetricNames.TRADE_REPORTS, "chainId", String.valueOf(chainId), "kind", normalizedKind).increment();
        webSocketPushService.onTxUpdate(view.chainId(), view.txHash(), view.kind(), view.status(), view.quoteId(), view.revertReason());
        long reportedAt = view.updatedAtMs();
        pollExecutor.execute(() -> pollReceipt(view, reportedAt));
        return view;
    }

    @Override
    public TradeStatusView status(long chainId, String txHash) {
        if (!TX_HASH.matcher(txHash).matches()) {
            throw new AnchorException("VALIDATION_ERROR", "交易哈希不正确");
        }
        TradeStatusView stored = loadStatus(chainId, txHash.toLowerCase());
        if (stored == null) {
            throw new AnchorException("NOT_FOUND", "未找到该交易追踪记录");
        }
        return stored;
    }

    private void pollReceipt(TradeStatusView initial, long reportedAtMs) {
        for (int i = 0; i < AnchorConstants.TRADE_POLL_MAX_ATTEMPTS; i++) {
            try {
                Thread.sleep(AnchorConstants.TRADE_POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            TradeStatusView current = loadStatus(initial.chainId(), initial.txHash());
            if (current != null && !"PENDING".equals(current.status())) {
                return;
            }
            try {
                List<ChainReceipt> receipts = rpcGateway.getReceipts(initial.chainId(), List.of(initial.txHash()));
                if (receipts.isEmpty()) {
                    continue;
                }
                ChainReceipt receipt = receipts.getFirst();
                String status = receipt.success() ? "CONFIRMED" : "FAILED";
                String revert = receipt.success() ? null : "execution reverted";
                long now = System.currentTimeMillis();
                TradeStatusView next = new TradeStatusView(initial.chainId(), initial.txHash(), initial.kind(), status,
                        initial.quoteId(), initial.userAddress(), revert, now);
                saveStatus(next);
                String chainTag = String.valueOf(initial.chainId());
                if (receipt.success()) {
                    meterRegistry.counter(AnchorMetricNames.TRADE_CONFIRMED, "chainId", chainTag, "kind", initial.kind()).increment();
                    meterRegistry.timer(AnchorMetricNames.TRADE_CONFIRM_LATENCY, "chainId", chainTag)
                            .record(java.time.Duration.ofMillis(Math.max(0, now - reportedAtMs)));
                } else {
                    meterRegistry.counter(AnchorMetricNames.TRADE_FAILED, "chainId", chainTag, "kind", initial.kind()).increment();
                }
                webSocketPushService.onTxUpdate(next.chainId(), next.txHash(), next.kind(), next.status(), next.quoteId(), next.revertReason());
                return;
            } catch (RuntimeException ex) {
                log.warn("轮询交易回执失败，txHash={}", initial.txHash());
            }
        }
        log.warn("交易回执轮询超时，txHash={}", initial.txHash());
    }

    private StoredQuote requireQuote(String quoteId) {
        StoredQuote quote = quoteQueryService.findStored(quoteId);
        if (quote == null) {
            throw new AnchorException("NOT_FOUND", "报价不存在或已过期");
        }
        if (quote.expiresAtEpochMs() < System.currentTimeMillis()) {
            throw new AnchorException("QUOTE_EXPIRED", "报价已过期，请重新报价");
        }
        return quote;
    }

    private static void requireTradeChain(long chainId) {
        if (chainId != AnchorConstants.TRADE_ALLOWED_CHAIN_ID) {
            throw new AnchorException("UNSUPPORTED", "P5a 仅允许在 Base Sepolia (84532) 准备/上报交易");
        }
    }

    private BigInteger readAllowance(long chainId, String token, String owner, String spender) {
        Function function = new Function(
                "allowance",
                Arrays.asList(new Address(owner), new Address(Addresses.lower(spender))),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        String raw = rpcGateway.ethCall(chainId, token, FunctionEncoder.encode(function));
        @SuppressWarnings("rawtypes")
        List<Type> decoded = FunctionReturnDecoder.decode(raw, function.getOutputParameters());
        if (decoded.isEmpty()) {
            return BigInteger.ZERO;
        }
        return ((Uint256) decoded.getFirst()).getValue();
    }

    private void saveStatus(TradeStatusView view) {
        try {
            redis.opsForValue().set(RedisKeys.tradeTx(view.chainId(), view.txHash()),
                    objectMapper.writeValueAsString(view), Duration.ofHours(AnchorConstants.TRADE_STATUS_TTL_HOURS));
        } catch (JsonProcessingException ex) {
            throw new AnchorException("INTERNAL_ERROR", "无法保存交易状态");
        }
    }

    private TradeStatusView loadStatus(long chainId, String txHash) {
        String raw = redis.opsForValue().get(RedisKeys.tradeTx(chainId, txHash));
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, TradeStatusView.class);
        } catch (JsonProcessingException ex) {
            log.warn("交易状态反序列化失败，txHash={}", txHash);
            return null;
        }
    }
}
