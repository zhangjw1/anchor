import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Header } from './components/Header';
import { WatchlistPanel } from './components/WatchlistPanel';
import { RecentSwapsPanel } from './components/RecentSwapsPanel';
import { BalancesPanel } from './components/BalancesPanel';
import { SwapPanel } from './components/SwapPanel';
import { FooterBar } from './components/FooterBar';
import { TxDetailModal } from './components/TxDetailModal';
import { AccountModal } from './components/AccountModal';
import { ApiError, SwapDto, WatchAddressDto, createWatchAddress, deleteWatchAddress, listSwaps, listWatchAddresses } from './api/anchor';
import { isMockMode } from './config/env';
import { useLiveWallet } from './hooks/useLiveWallet';
import { usePortfolioTokens } from './hooks/usePortfolioTokens';
import { useChainMetrics } from './hooks/useChainMetrics';
import { isWagmiChain } from './config/wagmi';
import { NETWORKS, selectableNetworks } from './mock/initialData';
import { WatchAddress, SwapTransaction, NetworkConfig, SwapStatus } from './types';
const POLL_MS = 3000;
const MOCK_ADDRESS = '0x94A1F29b3112E849C01132890bF20c1590748920';

function defaultNetwork(mock: boolean): NetworkConfig {
  return selectableNetworks(!mock)[0] ?? NETWORKS[0];
}

function shortAddress(address: string) {
  return `${address.slice(0, 6)}...${address.slice(-4)}`;
}

function humanAmount(amount: string, decimals: number | null) {
  const scale = decimals ?? 18;
  const negative = amount.startsWith('-');
  const digits = (negative ? amount.slice(1) : amount).replace(/^0+/, '') || '0';
  const padded = digits.padStart(scale + 1, '0');
  const whole = padded.slice(0, padded.length - scale);
  const fraction = padded.slice(padded.length - scale).replace(/0+$/, '');
  const value = Number(fraction ? `${whole}.${fraction}` : whole);
  return Number.isFinite(value) ? value : 0;
}

function tokenLabel(symbol: string | null, address: string) {
  return symbol || shortAddress(address);
}

function toWatch(item: WatchAddressDto): WatchAddress {
  return {
    id: item.id,
    name: item.name || shortAddress(item.address),
    address: item.address,
    tag: item.tag,
    addedAt: item.createdAt,
    note: item.note || undefined,
    swapCount: item.swapCount,
    lastActiveAt: item.lastActiveAt,
  };
}

function toStatus(item: SwapDto, confirmationBlocks: number): SwapStatus {
  if (item.confirmStatus === 'REORGED') {
    return { type: 'reorg', label: '已回滚 reorg' };
  }
  if (item.confirmStatus === 'CONFIRMING') {
    const current = Math.max(0, item.confirmations ?? 0);
    return { type: 'confirming', current, total: confirmationBlocks };
  }
  if (item.confirmStatus === 'FINALIZED') {
    return { type: 'finalized', label: '已最终确认' };
  }
  return { type: 'confirmed', label: '已确认' };
}

function hopPathSymbols(item: SwapDto, fromToken: string, toToken: string): string[] | undefined {
  const hops = item.hops;
  if (!hops || hops.length === 0) {
    return undefined;
  }
  const symbols = [tokenLabel(hops[0].tokenIn.symbol, hops[0].tokenIn.address)];
  for (const hop of hops) {
    symbols.push(tokenLabel(hop.tokenOut.symbol, hop.tokenOut.address));
  }
  if (symbols.length === 2 && symbols[0] === fromToken && symbols[1] === toToken) {
    return undefined;
  }
  return symbols;
}

function toSwap(item: SwapDto, names: Map<string, string>, confirmationBlocks: number): SwapTransaction {
  const fromToken = tokenLabel(item.tokenIn.symbol, item.tokenIn.address);
  const toToken = tokenLabel(item.tokenOut.symbol, item.tokenOut.address);
  const aggregator = item.aggregator?.trim() || undefined;
  const hops = item.hops ?? [];
  return {
    id: item.id,
    txHash: item.txHash,
    direction: item.direction,
    pair: {
      fromToken,
      toToken,
      hops: hopPathSymbols(item, fromToken, toToken),
      isAggregator: Boolean(aggregator),
      aggregatorLabel: aggregator,
    },
    hopDetails: hops.map((hop) => ({
      hopIndex: hop.hopIndex,
      dex: hop.dex,
      pool: hop.pool,
      tokenIn: tokenLabel(hop.tokenIn.symbol, hop.tokenIn.address),
      tokenOut: tokenLabel(hop.tokenOut.symbol, hop.tokenOut.address),
      amountIn: humanAmount(hop.tokenIn.amount, hop.tokenIn.decimals),
      amountOut: humanAmount(hop.tokenOut.amount, hop.tokenOut.decimals),
      logIndex: hop.logIndex,
    })),
    dex: item.dex,
    amount: '',
    rawAmount: humanAmount(item.tokenOut.amount, item.tokenOut.decimals),
    amountToken: toToken,
    paidAmount: humanAmount(item.tokenIn.amount, item.tokenIn.decimals),
    status: toStatus(item, confirmationBlocks),
    timestamp: item.timestamp,
    senderAddress: item.watchedAddress,
    recipientAddress: item.watchedAddress,
    gasFeeUsd: 0,
    gasUsed: 0,
    blockNumber: item.blockNumber,
    watchedAddressMatch: names.get(item.watchedAddress) || shortAddress(item.watchedAddress),
  };
}

export default function App() {
  const mock = isMockMode();
  const liveWallet = useLiveWallet();
  const [network, setNetwork] = useState<NetworkConfig>(() => defaultNetwork(isMockMode()));

  useEffect(() => {
    if (mock) {
      return;
    }
    if (!isWagmiChain(network.chainId)) {
      setNetwork(defaultNetwork(false));
    }
  }, [mock, network.chainId]);
  const [watchlist, setWatchlist] = useState<WatchAddress[]>([]);
  const [selectedWatchId, setSelectedWatchId] = useState<string | null>(null);
  const [swaps, setSwaps] = useState<SwapTransaction[]>([]);
  const [mockAddress, setMockAddress] = useState<string>(MOCK_ADDRESS);
  const [isStreaming, setIsStreaming] = useState<boolean>(true);
  const [feedError, setFeedError] = useState<string | null>(null);
  const [activeTx, setActiveTx] = useState<SwapTransaction | null>(null);
  const [isAccountModalOpen, setIsAccountModalOpen] = useState<boolean>(false);
  const [mobileTab, setMobileTab] = useState<'watch' | 'swaps' | 'swap'>('swaps');
  const selectedAddressRef = useRef<string | undefined>(undefined);
  selectedAddressRef.current = watchlist.find((item) => item.id === selectedWatchId)?.address;

  const userAddress = mock ? mockAddress : (liveWallet.address || '');
  const chainMetrics = useChainMetrics(network.chainId, !mock);
  const { tokens, setTokens, error: assetError, refresh: refreshAssets } = usePortfolioTokens(
    network.chainId,
    userAddress || undefined,
  );

  const refreshFeed = useCallback(async (chainId: number, confirmationBlocks: number, address?: string) => {
    const [addresses, page] = await Promise.all([
      listWatchAddresses(chainId),
      listSwaps(chainId, address),
    ]);
    const names = new Map(addresses.map((item) => [item.address, item.name || shortAddress(item.address)]));
    setWatchlist(addresses.map(toWatch));
    setSwaps(page.items.map((item) => toSwap(item, names, confirmationBlocks)));
    setFeedError(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const load = () => {
      refreshFeed(network.chainId, network.confirmationBlocks, selectedAddressRef.current).catch((error: unknown) => {
        if (!cancelled) {
          setFeedError(error instanceof ApiError ? error.message : '地址簿或成交列表加载失败');
        }
      });
    };
    load();
    if (!isStreaming) {
      return () => {
        cancelled = true;
      };
    }
    const timer = window.setInterval(load, POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [isStreaming, network.chainId, network.confirmationBlocks, refreshFeed, selectedWatchId]);

  const handleAddWatchAddress = async (address: string, name?: string, tag: WatchAddress['tag'] = 'WATCH') => {
    await createWatchAddress({ chainId: network.chainId, address, name, tag });
    const selected = watchlist.find((item) => item.id === selectedWatchId);
    await refreshFeed(network.chainId, network.confirmationBlocks, selected?.address);
  };

  const handleDeleteWatchAddress = async (id: string) => {
    await deleteWatchAddress(id);
    if (selectedWatchId === id) {
      setSelectedWatchId(null);
    }
    await refreshFeed(network.chainId, network.confirmationBlocks);
  };

  const handleExecuteSwap = (fromToken: string, toToken: string, fromAmt: number, toAmt: number, hash: string) => {
    if (!tokens[fromToken] || !tokens[toToken] || fromToken === toToken || !Number.isFinite(fromAmt) || !Number.isFinite(toAmt) || fromAmt <= 0 || toAmt <= 0 || fromAmt > tokens[fromToken].balance || !/^0x[0-9a-fA-F]{64}$/.test(hash)) return;
    setTokens((prev) => {
      const fromObj = prev[fromToken];
      const toObj = prev[toToken];
      if (!fromObj || !toObj) return prev;
      return {
        ...prev,
        [fromToken]: { ...fromObj, balance: fromObj.balance - fromAmt },
        [toToken]: { ...toObj, balance: toObj.balance + toAmt },
      };
    });
  };

  const handleFaucet = (symbol: string, amt: number) => {
    if (!mock || !Number.isFinite(amt) || amt <= 0) return;
    setTokens((prev) => {
      const current = prev[symbol];
      if (!current) return prev;
      return {
        ...prev,
        [symbol]: {
          ...current,
          balance: current.balance + amt,
        },
      };
    });
  };

  const bannerError = feedError || assetError;

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-[#f3f5f8] text-slate-800">
      <Header
        currentNetwork={network}
        onSelectNetwork={setNetwork}
        behindBlocks={chainMetrics.lagBlocks}
        confirmationTarget={network.confirmationBlocks}
        isSyncing={isStreaming}
        onOpenAccountModal={() => setIsAccountModalOpen(true)}
      />

      <div className="md:hidden flex border-b border-slate-200 bg-white text-xs font-semibold">
        <button
          onClick={() => setMobileTab('watch')}
          className={`flex-1 py-2 text-center border-b-2 cursor-pointer ${
            mobileTab === 'watch' ? 'border-blue-600 text-blue-600' : 'border-transparent text-slate-600'
          }`}
        >
          地址簿
        </button>
        <button
          onClick={() => setMobileTab('swaps')}
          className={`flex-1 py-2 text-center border-b-2 cursor-pointer ${
            mobileTab === 'swaps' ? 'border-blue-600 text-blue-600' : 'border-transparent text-slate-600'
          }`}
        >
          Recent Swaps
        </button>
        <button
          onClick={() => setMobileTab('swap')}
          className={`flex-1 py-2 text-center border-b-2 cursor-pointer ${
            mobileTab === 'swap' ? 'border-blue-600 text-blue-600' : 'border-transparent text-slate-600'
          }`}
        >
          余额 & 兑换
        </button>
      </div>

      {bannerError && (
        <div className="bg-rose-50 text-rose-700 text-xs px-4 py-2 border-b border-rose-100 shrink-0">{bannerError}</div>
      )}
      {!mock && !liveWallet.isConnected && (
        <div className="bg-amber-50 text-amber-800 text-xs px-4 py-2 border-b border-amber-100 shrink-0">
          请先连接钱包以查看真实余额与报价（VITE_USE_MOCK=false）
        </div>
      )}

      <main className="flex-1 flex overflow-hidden">
        <section
          className={`w-full md:w-[240px] lg:w-[260px] shrink-0 border-r border-slate-200 h-full ${
            mobileTab === 'watch' ? 'flex' : 'hidden md:flex'
          }`}
        >
          <WatchlistPanel
            watchlist={watchlist}
            swaps={swaps}
            selectedAddressId={selectedWatchId}
            onSelectAddress={setSelectedWatchId}
            onAddAddress={handleAddWatchAddress}
            onDeleteAddress={handleDeleteWatchAddress}
            explorerUrl={network.explorerUrl}
          />
        </section>

        <section
          className={`flex-1 h-full min-w-0 ${
            mobileTab === 'swaps' ? 'flex flex-col' : 'hidden md:flex flex-col'
          }`}
        >
          <RecentSwapsPanel
            swaps={swaps}
            onSelectTx={setActiveTx}
            headBlock={chainMetrics.headBlock}
            processedBlock={chainMetrics.processedBlock}
            isStreaming={isStreaming}
            onToggleStreaming={() => setIsStreaming(!isStreaming)}
            explorerUrl={network.explorerUrl}
          />
        </section>

        <section
          className={`w-full md:w-[320px] lg:w-[350px] shrink-0 border-l border-slate-200 flex flex-col h-full overflow-y-auto bg-white ${
            mobileTab === 'swap' ? 'flex' : 'hidden md:flex'
          }`}
        >
          <BalancesPanel
            userAddress={userAddress || '未连接'}
            tokens={tokens}
            onRefreshBalances={refreshAssets}
            explorerUrl={network.explorerUrl}
          />

          <SwapPanel
            tokens={tokens}
            onExecuteSwap={handleExecuteSwap}
            chainId={network.chainId}
            live={!mock}
          />
        </section>
      </main>

      <FooterBar metrics={chainMetrics} mock={mock} />

      <TxDetailModal
        tx={activeTx ? swaps.find((item) => item.id === activeTx.id) ?? activeTx : null}
        onClose={() => setActiveTx(null)}
        explorerUrl={network.explorerUrl}
      />

      <AccountModal
        isOpen={isAccountModalOpen}
        onClose={() => setIsAccountModalOpen(false)}
        userAddress={userAddress || ''}
        onUpdateAddress={setMockAddress}
        tokens={tokens}
        onFaucet={handleFaucet}
        network={network}
        mockMode={mock}
      />
    </div>
  );
}
