import { WatchAddress, SwapTransaction, Token, NetworkConfig } from '../types';
import { CONFIRMATION_TARGET, formatAmount } from '../utils/swap';

const MOCK_NOW = Date.now();

export const INITIAL_WATCHLIST: WatchAddress[] = [
  { id: 'w-1', name: 'Smart Whale #1', address: '0x71C86B408655373fB1953D4b6F0F239bB7b23e92', tag: 'WATCH', addedAt: MOCK_NOW - 259200000 },
  { id: 'w-2', name: 'DEX Arbitrageur', address: '0x1111111254fb6c44bac0bed2854e76f906434444', tag: 'ROUTER', addedAt: MOCK_NOW - 604800000 },
  { id: 'w-3', name: 'Base Alpha', address: '0x539c381c812c3882759902678832a8848419f18a', tag: 'WHALE', addedAt: MOCK_NOW - 172800000 },
  { id: 'w-4', name: 'MEV Searcher', address: '0x8849c3218bead48482939b4009121a83bb921822', tag: 'MEV', addedAt: MOCK_NOW - 86400000 },
];

export const INITIAL_TOKENS: Record<string, Token> = {
  ETH: { symbol: 'ETH', name: 'Ethereum', balance: 12.458, decimals: 18, priceUsd: 3402.12, address: '0x0000000000000000000000000000000000000000' },
  WETH: { symbol: 'WETH', name: 'Wrapped Ether', balance: 8.924, decimals: 18, priceUsd: 3402.12, address: '0x4200000000000000000000000000000000000006' },
  USDC: { symbol: 'USDC', name: 'USD Coin', balance: 45230, decimals: 6, priceUsd: 1, address: '0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913' },
  DEGEN: { symbol: 'DEGEN', name: 'Degen', balance: 0, decimals: 18, priceUsd: 0.0094, address: '0x4ed4E862860beD51a9570b96d89aF5E1B0Efefed' },
  AERO: { symbol: 'AERO', name: 'Aerodrome Finance', balance: 0, decimals: 18, priceUsd: 1.15, address: '0x940181a94A35A4569E4529A3CDfB74e38FD98631' },
  cbETH: { symbol: 'cbETH', name: 'Coinbase Wrapped Staked ETH', balance: 0, decimals: 18, priceUsd: 3624.5, address: '0x2Ae3F1Ec7F1F5012CFEab0185bfc7aa3cf0DEc22' },
};

interface MockSwapSpec {
  from: string;
  to: string;
  paid: number;
  received: number;
  dex: SwapTransaction['dex'];
  watchIndex: number;
  secondsAgo: number;
  confirmations?: number;
  reorg?: boolean;
  hops?: string[];
}

const MOCK_SWAP_SPECS: MockSwapSpec[] = [
  { from: 'USDC', to: 'WETH', paid: 4082.54, received: 1.2, dex: 'Uniswap V3', watchIndex: 0, secondsAgo: 4, reorg: true },
  { from: 'USDC', to: 'DEGEN', paid: 500, received: 240500, dex: '1inch', watchIndex: 1, secondsAgo: 12, confirmations: 3, hops: ['USDC', 'WETH', 'DEGEN'] },
  { from: 'WETH', to: 'AERO', paid: 5.01, received: 14800, dex: 'Aerodrome', watchIndex: 0, secondsAgo: 18, confirmations: 5 },
  { from: 'DEGEN', to: 'USDC', paid: 880000, received: 8272, dex: 'Uniswap V3', watchIndex: 2, secondsAgo: 45 },
  { from: 'cbETH', to: 'WETH', paid: 4.23, received: 4.5, dex: 'Aerodrome', watchIndex: 1, secondsAgo: 72 },
  { from: 'USDC', to: 'WETH', paid: 2891.8, received: 0.85, dex: 'Uniswap V3', watchIndex: 0, secondsAgo: 110 },
  { from: 'WETH', to: 'USDC', paid: 2, received: 6798.8, dex: 'SushiSwap', watchIndex: 3, secondsAgo: 140 },
  { from: 'USDC', to: 'AERO', paid: 2300, received: 1998.2, dex: 'Aerodrome', watchIndex: 2, secondsAgo: 175 },
  { from: 'AERO', to: 'USDC', paid: 3200, received: 3676.32, dex: 'Uniswap V3', watchIndex: 3, secondsAgo: 210 },
  { from: 'ETH', to: 'USDC', paid: 0.6, received: 2040.25, dex: '1inch', watchIndex: 0, secondsAgo: 260 },
  { from: 'USDC', to: 'cbETH', paid: 1812.25, received: 0.4995, dex: 'Curve', watchIndex: 2, secondsAgo: 310 },
  { from: 'WETH', to: 'DEGEN', paid: 0.25, received: 90350, dex: 'Uniswap V3', watchIndex: 3, secondsAgo: 370 },
];

export const INITIAL_SWAPS: SwapTransaction[] = MOCK_SWAP_SPECS.map((spec, index) => {
  const wallet = INITIAL_WATCHLIST[spec.watchIndex];
  return {
    id: `tx-${index + 1}`,
    txHash: `0x${(index + 1).toString(16).padStart(64, '0')}`,
    direction: spec.to === 'USDC' ? 'SELL' : 'BUY',
    pair: { fromToken: spec.from, toToken: spec.to, hops: spec.hops, isAggregator: Boolean(spec.hops), aggregatorLabel: spec.hops ? '聚合器' : undefined },
    dex: spec.dex,
    amount: `+${formatAmount(spec.received, spec.to, true)} ${spec.to}`,
    rawAmount: spec.received,
    amountToken: spec.to,
    paidAmount: spec.paid,
    status: spec.reorg ? { type: 'reorg', label: '已回滚 reorg' } : spec.confirmations ? { type: 'confirming', current: spec.confirmations, total: CONFIRMATION_TARGET } : { type: 'finalized', label: '已确认' },
    timestamp: MOCK_NOW - spec.secondsAgo * 1000,
    senderAddress: wallet.address,
    recipientAddress: wallet.address,
    gasFeeUsd: 0.018,
    gasUsed: 138402,
    blockNumber: 21432908 - Math.ceil(spec.secondsAgo / 2),
    watchedAddressMatch: wallet.name,
  };
});

export const NETWORKS: NetworkConfig[] = [
  {
    id: 'base-mainnet',
    name: 'Base Mainnet',
    shortName: 'Base',
    chainId: 8453,
    blockTimeSec: 2,
    explorerUrl: 'https://basescan.org',
  },
  {
    id: 'base-sepolia',
    name: 'Base Sepolia Testnet',
    shortName: 'Sepolia',
    chainId: 84532,
    blockTimeSec: 2,
    explorerUrl: 'https://sepolia.basescan.org',
  },
  {
    id: 'ethereum',
    name: 'Ethereum Mainnet',
    shortName: 'ETH',
    chainId: 1,
    blockTimeSec: 12,
    explorerUrl: 'https://etherscan.io',
  },
  {
    id: 'arbitrum-one',
    name: 'Arbitrum One',
    shortName: 'Arbitrum',
    chainId: 42161,
    blockTimeSec: 0.25,
    explorerUrl: 'https://arbiscan.io',
  },
];
