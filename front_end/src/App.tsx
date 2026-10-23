import React, { useState, useEffect, useRef } from 'react';
import { Header } from './components/Header';
import { WatchlistPanel } from './components/WatchlistPanel';
import { RecentSwapsPanel } from './components/RecentSwapsPanel';
import { BalancesPanel } from './components/BalancesPanel';
import { SwapPanel } from './components/SwapPanel';
import { FooterBar } from './components/FooterBar';
import { TxDetailModal } from './components/TxDetailModal';
import { AccountModal } from './components/AccountModal';
import { 
  INITIAL_WATCHLIST, 
  INITIAL_SWAPS, 
  INITIAL_TOKENS, 
  NETWORKS 
} from './mock/initialData';
import { WatchAddress, SwapTransaction, Token, NetworkConfig } from './types';
import { CONFIRMATION_TARGET, createMockHash, FEED_LIMIT, formatAmount } from './utils/swap';

export default function App() {
  const [network, setNetwork] = useState<NetworkConfig>(NETWORKS[0]);
  const [watchlist, setWatchlist] = useState<WatchAddress[]>(INITIAL_WATCHLIST);
  const [selectedWatchId, setSelectedWatchId] = useState<string | null>(null);
  const [swaps, setSwaps] = useState<SwapTransaction[]>(INITIAL_SWAPS);
  const [tokens, setTokens] = useState<Record<string, Token>>(INITIAL_TOKENS);
  const [userAddress, setUserAddress] = useState<string>('0x94A1F29b3112E849C01132890bF20c1590748920');
  const [latestBlock, setLatestBlock] = useState<number>(21432908);
  const [isStreaming, setIsStreaming] = useState<boolean>(true);
  const latestBlockRef = useRef(latestBlock);
  useEffect(() => { latestBlockRef.current = latestBlock; }, [latestBlock]);
  const [activeTx, setActiveTx] = useState<SwapTransaction | null>(null);
  const [isAccountModalOpen, setIsAccountModalOpen] = useState<boolean>(false);

  // Responsive mobile active tab (for small screens)
  const [mobileTab, setMobileTab] = useState<'watch' | 'swaps' | 'swap'>('swaps');

  // Handle adding a new address to watchlist
  const handleAddWatchAddress = (address: string, name?: string, tag: WatchAddress['tag'] = 'WATCH') => {
    if (!/^0x[0-9a-fA-F]{40}$/.test(address) || watchlist.some((item) => item.address.toLowerCase() === address.toLowerCase())) return;
    const newEntry: WatchAddress = {
      id: `w-${Date.now()}`,
      name: name || `Watched ${address.slice(0, 6)}...`,
      address,
      tag,
      addedAt: Date.now(),
    };
    setWatchlist((prev) => [newEntry, ...prev]);
  };

  // Handle deleting an address from watchlist
  const handleDeleteWatchAddress = (id: string) => {
    setWatchlist((prev) => prev.filter((w) => w.id !== id));
    if (selectedWatchId === id) {
      setSelectedWatchId(null);
    }
  };

  // Filter swaps if a watchlist item is chosen
  const activeWatchedAddress = watchlist.find((w) => w.id === selectedWatchId);
  const displaySwaps = activeWatchedAddress
    ? swaps.filter(
        (s) =>
          s.senderAddress.toLowerCase() === activeWatchedAddress.address.toLowerCase() ||
          s.recipientAddress.toLowerCase() === activeWatchedAddress.address.toLowerCase() ||
          s.watchedAddressMatch === activeWatchedAddress.name
      )
    : swaps;

  // Simulate on-chain block ticking and progressive confirmations
  useEffect(() => {
    if (!isStreaming) return;

    const blockInterval = setInterval(() => {
      setLatestBlock((b) => b + 1);

      // Advance any confirming transactions
      setSwaps((prevSwaps) =>
        prevSwaps.map((tx) => {
          if (tx.status.type === 'confirming') {
            const nextCurrent = tx.status.current + 1;
            if (nextCurrent >= tx.status.total) {
              return {
                ...tx,
                status: { type: 'finalized', label: '已确认' },
              };
            }
            return {
              ...tx,
              status: { ...tx.status, current: nextCurrent },
            };
          }
          return tx;
        })
      );
    }, network.blockTimeSec * 1000);

    return () => clearInterval(blockInterval);
  }, [isStreaming, network.blockTimeSec]);

  // Periodic random new incoming swaps to keep the dashboard live and authentic
  useEffect(() => {
    if (!isStreaming) return;

    const randomFeedInterval = setInterval(() => {
      const dexes: SwapTransaction['dex'][] = ['Uniswap V3', '1inch', 'Aerodrome', 'SushiSwap'];
      const pairs = [
        { from: 'USDC', to: 'WETH', amount: '+0.45 WETH', raw: 0.45, token: 'WETH' },
        { from: 'WETH', to: 'AERO', amount: '+3,200 AERO', raw: 3200, token: 'AERO' },
        { from: 'USDC', to: 'DEGEN', amount: '+95.2k DEGEN', raw: 95200, token: 'DEGEN', isAgg: true },
        { from: 'cbETH', to: 'WETH', amount: '+1.80 WETH', raw: 1.8, token: 'WETH' },
      ];

      const chosenDex = dexes[Math.floor(Math.random() * dexes.length)];
      const chosenPair = pairs[Math.floor(Math.random() * pairs.length)];
      const isWatched = Math.random() > 0.4;
      const matchedWhale = isWatched ? watchlist[Math.floor(Math.random() * watchlist.length)] : null;

      const newTx: SwapTransaction = {
        id: `tx-${Date.now()}`,
        txHash: createMockHash(),
        direction: chosenPair.to === 'USDC' ? 'SELL' : 'BUY',
        pair: {
          fromToken: chosenPair.from,
          toToken: chosenPair.to,
          hops: chosenPair.isAgg ? ['USDC', 'WETH', 'DEGEN'] : undefined,
          isAggregator: chosenPair.isAgg,
          aggregatorLabel: chosenPair.isAgg ? '聚合器' : undefined,
        },
        dex: chosenDex,
        amount: chosenPair.amount,
        rawAmount: chosenPair.raw,
        amountToken: chosenPair.token,
        paidAmount: chosenPair.raw * INITIAL_TOKENS[chosenPair.to].priceUsd / INITIAL_TOKENS[chosenPair.from].priceUsd,
        status: { type: 'confirming', current: 1, total: CONFIRMATION_TARGET },
        timestamp: Date.now(),
        timeDisplay: '1s ago',
        senderAddress: matchedWhale ? matchedWhale.address : `0x${Array.from({ length: 40 }, () => Math.floor(Math.random() * 16).toString(16)).join('')}`,
        recipientAddress: matchedWhale ? matchedWhale.address : `0x${Array.from({ length: 40 }, () => Math.floor(Math.random() * 16).toString(16)).join('')}`,
        gasFeeUsd: 0.015,
        gasUsed: 128000,
        blockNumber: latestBlockRef.current,
        watchedAddressMatch: matchedWhale ? matchedWhale.name : undefined,
      };

      setSwaps((prev) => [newTx, ...prev.slice(0, FEED_LIMIT - 1)]);
    }, 6000);

    return () => clearInterval(randomFeedInterval);
  }, [isStreaming, watchlist]);

  // Execute Swap action from right panel
  const handleExecuteSwap = (fromToken: string, toToken: string, fromAmt: number, toAmt: number, hash: string) => {
    if (!tokens[fromToken] || !tokens[toToken] || fromToken === toToken || !Number.isFinite(fromAmt) || !Number.isFinite(toAmt) || fromAmt <= 0 || toAmt <= 0 || fromAmt > tokens[fromToken].balance || !/^0x[0-9a-fA-F]{64}$/.test(hash)) return;
    // Deduct and credit
    setTokens((prev) => {
      const fromObj = prev[fromToken];
      const toObj = prev[toToken];
      if (!fromObj || !toObj) return prev;

      return {
        ...prev,
        [fromToken]: {
          ...fromObj,
          balance: fromObj.balance - fromAmt,
        },
        [toToken]: {
          ...toObj,
          balance: toObj.balance + toAmt,
        },
      };
    });

    // Create record in recent swaps
    const newTx: SwapTransaction = {
      id: `tx-user-${Date.now()}`,
      txHash: hash,
      direction: toToken === 'USDC' ? 'SELL' : 'BUY',
      pair: {
        fromToken,
        toToken,
      },
      dex: 'Uniswap V3',
      amount: `+${formatAmount(toAmt, toToken, true)} ${toToken}`,
      rawAmount: toAmt,
      amountToken: toToken,
      paidAmount: fromAmt,
      status: { type: 'finalized', label: '已确认' },
      timestamp: Date.now(),
      timeDisplay: '刚刚',
      senderAddress: userAddress,
      recipientAddress: userAddress,
      gasFeeUsd: 0.019,
      gasUsed: 145000,
      blockNumber: latestBlock,
    };

    setSwaps((prev) => [newTx, ...prev.slice(0, FEED_LIMIT - 1)]);
  };

  // Simulate Reorg event
  const handleTriggerReorg = () => {

    // Pick a recent confirming or finalized swap and roll it back
    setSwaps((prev) => {
      const nonReorgIdx = prev.findIndex((s) => s.status.type !== 'reorg');
      if (nonReorgIdx === -1) return prev;

      const updated = [...prev];
      updated[nonReorgIdx] = {
        ...updated[nonReorgIdx],
        status: { type: 'reorg', label: '已回滚 reorg' },
      };
      return updated;
    });
  };

  // Faucet claim for testing
  const handleFaucet = (symbol: string, amt: number) => {
    if (!Number.isFinite(amt) || amt <= 0) return;
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

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-[#f3f5f8] text-slate-800">
      {/* Top Header */}
      <Header
        currentNetwork={network}
        onSelectNetwork={setNetwork}
        behindBlocks={0}
        confirmationTarget={CONFIRMATION_TARGET}
        isSyncing={isStreaming}
        onOpenAccountModal={() => setIsAccountModalOpen(true)}
      />

      {/* Mobile Tab Navigation (visible only on small screens) */}
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

      {/* Main 3-Column Dashboard Body */}
      <main className="flex-1 flex overflow-hidden">
        {/* Left Column: Watchlist (Hidden on mobile unless selected) */}
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

        {/* Center Column: Recent Swaps Feed */}
        <section
          className={`flex-1 h-full min-w-0 ${
            mobileTab === 'swaps' ? 'flex flex-col' : 'hidden md:flex flex-col'
          }`}
        >
          <RecentSwapsPanel
            swaps={displaySwaps}
            onSelectTx={setActiveTx}
            latestBlock={latestBlock}
            isStreaming={isStreaming}
            onToggleStreaming={() => setIsStreaming(!isStreaming)}
            onTriggerReorgSimulation={handleTriggerReorg}
            explorerUrl={network.explorerUrl}
          />
        </section>

        {/* Right Column: Balances & Swap Execution */}
        <section
          className={`w-full md:w-[320px] lg:w-[350px] shrink-0 border-l border-slate-200 flex flex-col h-full overflow-y-auto bg-white ${
            mobileTab === 'swap' ? 'flex' : 'hidden md:flex'
          }`}
        >
          {/* Top of right: Balances */}
          <BalancesPanel
            userAddress={userAddress}
            tokens={tokens}
            explorerUrl={network.explorerUrl}
          />

          {/* Bottom of right: Swap Widget */}
          <SwapPanel
            tokens={tokens}
            onExecuteSwap={handleExecuteSwap}
          />
        </section>
      </main>

      {/* Bottom Telemetry Bar */}
      <FooterBar />

      {/* Deep Tx Inspector Modal */}
      <TxDetailModal
        tx={activeTx ? swaps.find((item) => item.id === activeTx.id) ?? activeTx : null}
        onClose={() => setActiveTx(null)}
        explorerUrl={network.explorerUrl}
      />

      {/* Account / Faucet Modal */}
      <AccountModal
        isOpen={isAccountModalOpen}
        onClose={() => setIsAccountModalOpen(false)}
        userAddress={userAddress}
        onUpdateAddress={setUserAddress}
        tokens={tokens}
        onFaucet={handleFaucet}
        network={network}
      />
    </div>
  );
}
