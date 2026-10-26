package com.anchor.common.manager;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainTransaction;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.support.Addresses;
import okhttp3.OkHttpClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class Web3jRpcEndpoint implements RpcEndpoint {

    private final String url;
    private final TokenBucket bucket;
    private final Web3j web3j;

    public Web3jRpcEndpoint(String url, double requestsPerSecond) {
        this.url = url;
        this.bucket = new TokenBucket(requestsPerSecond);
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(AnchorConstants.RPC_TIMEOUT_SECONDS))
                .connectTimeout(Duration.ofSeconds(AnchorConstants.RPC_TIMEOUT_SECONDS))
                .readTimeout(Duration.ofSeconds(AnchorConstants.RPC_TIMEOUT_SECONDS))
                .build();
        this.web3j = Web3j.build(new HttpService(url, client));
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public TokenBucket bucket() {
        return bucket;
    }

    @Override
    public long latestBlockNumber() throws Exception {
        return web3j.ethBlockNumber().send().getBlockNumber().longValue();
    }

    @Override
    public ChainBlock getBlock(long blockNumber) throws Exception {
        EthBlock response = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), true).send();
        throwIfError(response);
        EthBlock.Block block = response.getBlock();
        if (block == null) {
            throw new IllegalStateException("block not found " + blockNumber);
        }
        List<ChainTransaction> transactions = new ArrayList<>();
        for (EthBlock.TransactionResult<?> item : block.getTransactions()) {
            EthBlock.TransactionObject tx = (EthBlock.TransactionObject) item.get();
            transactions.add(new ChainTransaction(
                    Addresses.lower(tx.getHash()),
                    Addresses.lower(tx.getFrom()),
                    Addresses.lower(tx.getTo()),
                    tx.getValue(),
                    tx.getInput()));
        }
        return new ChainBlock(
                block.getNumber().longValue(),
                Addresses.lower(block.getHash()),
                Addresses.lower(block.getParentHash()),
                block.getTimestamp().longValue(),
                List.copyOf(transactions));
    }

    @Override
    public List<ChainReceipt> getReceipts(List<String> txHashes) throws Exception {
        if (txHashes.isEmpty()) {
            return List.of();
        }
        BatchRequest batch = web3j.newBatch();
        for (String hash : txHashes) {
            batch.add(web3j.ethGetTransactionReceipt(hash));
        }
        List<? extends Response<?>> responses = batch.send().getResponses();
        List<ChainReceipt> receipts = new ArrayList<>();
        for (Response<?> response : responses) {
            throwIfError(response);
            EthGetTransactionReceipt typed = (EthGetTransactionReceipt) response;
            typed.getTransactionReceipt().ifPresent(receipt -> receipts.add(toReceipt(receipt)));
        }
        return receipts;
    }

    @Override
    public String ethCall(String to, String data) throws Exception {
        EthCall response = web3j.ethCall(Transaction.createEthCallTransaction(null, to, data), DefaultBlockParameterName.LATEST).send();
        throwIfError(response);
        return response.getValue();
    }

    private static ChainReceipt toReceipt(TransactionReceipt receipt) {
        List<ChainLog> logs = new ArrayList<>();
        for (Log log : receipt.getLogs()) {
            logs.add(new ChainLog(
                    Addresses.lower(log.getAddress()),
                    log.getTopics().stream().map(Addresses::lower).toList(),
                    log.getData(),
                    log.getLogIndex().intValue()));
        }
        boolean success = "0x1".equalsIgnoreCase(receipt.getStatus());
        return new ChainReceipt(Addresses.lower(receipt.getTransactionHash()), success, receipt.getGasUsed(), List.copyOf(logs));
    }

    private static void throwIfError(Response<?> response) {
        if (response.hasError()) {
            throw new IllegalStateException(response.getError().getMessage());
        }
    }
}
