export interface WatchAddress {
  id: string;
  name: string;
  address: string;
  tag: 'WATCH' | 'ROUTER' | 'MEV' | 'WHALE' | 'KOL';
  addedAt: number;
  note?: string;
}

export type SwapDirection = 'BUY' | 'SELL';

export type SwapStatus = 
  | { type: 'reorg'; label: string }
  | { type: 'confirming'; current: number; total: number }
  | { type: 'finalized'; label: string };

export interface SwapTransaction {
  id: string;
  txHash: string;
  direction: SwapDirection;
  pair: {
    fromToken: string;
    toToken: string;
    hops?: string[]; // e.g. ['USDC', 'WETH', 'DEGEN']
    isAggregator?: boolean;
    aggregatorLabel?: string;
  };
  dex: 'Uniswap V3' | '1inch' | 'Aerodrome' | 'Curve' | 'SushiSwap' | 'Maverick';
  amount: string; // e.g. "+1.20 WETH" or "+240.5k DEGEN"
  rawAmount: number;
  amountToken: string;
  paidAmount: number;
  status: SwapStatus;
  timestamp: number; // ms
  timeDisplay?: string; // e.g. "4s ago"
  senderAddress: string;
  recipientAddress: string;
  gasFeeUsd: number;
  gasUsed: number;
  blockNumber: number;
  watchedAddressMatch?: string; // name of watched wallet if triggered
}

export interface Token {
  symbol: string;
  name: string;
  balance: number;
  decimals: number;
  priceUsd: number;
  icon?: string;
  address: string;
}

export interface NetworkConfig {
  id: string;
  name: string;
  shortName: string;
  chainId: number;
  blockTimeSec: number;
  explorerUrl: string;
}
