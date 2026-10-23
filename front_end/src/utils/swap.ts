import { Token } from '../types';

export const CONFIRMATION_TARGET = 12;
export const FEED_LIMIT = 50;
export const QUOTE_DELAY_MS = 650;
export const TRANSACTION_STEP_MS = 1200;
export const DEFAULT_SLIPPAGE = 0.5;
export const MOCK_INITIAL_NONCE = 42;
export const MAX_SLIPPAGE = 50;
export const WALLET_SYMBOLS = ['ETH', 'WETH', 'USDC'] as const;

/** 根据 mock 流动性差异计算四个费率档的实际到手报价。 */
const MOCK_POOLS = [
  { fee: 0.01, priceImpact: 0.0025 },
  { fee: 0.05, priceImpact: 0.0003 },
  { fee: 0.3, priceImpact: 0.0002 },
  { fee: 1, priceImpact: 0.0001 },
] as const;

export function getRouteQuotes(amount: number, from?: Token, to?: Token) {
  if (!from || !to || from.symbol === to.symbol || !Number.isFinite(amount) || amount <= 0 || from.priceUsd <= 0 || to.priceUsd <= 0) return [];
  return MOCK_POOLS.map((pool) => ({
    ...pool,
    amount: Math.floor(amount * from.priceUsd / to.priceUsd * (1 - pool.fee / 100) * (1 - pool.priceImpact) * 10 ** to.decimals) / 10 ** to.decimals,
  }));
}

export function getMinimumReceived(amount: number, slippage: number, decimals: number) {
  return Math.floor(amount * (1 - slippage / 100) * 10 ** decimals) / 10 ** decimals;
}

export function formatAmount(amount: number, symbol: string, compact = false) {
  const digits = symbol === 'USDC' ? 2 : 6;
  if (compact && amount >= 1000 && symbol !== 'USDC') return `${(amount / 1000).toLocaleString('en-US', { maximumFractionDigits: 1 })}k`;
  return amount.toLocaleString('en-US', { minimumFractionDigits: symbol === 'USDC' ? 2 : 0, maximumFractionDigits: digits });
}

export function formatRelativeTime(timestamp: number, now = Date.now()) {
  const seconds = Math.max(0, Math.floor((now - timestamp) / 1000));
  if (seconds < 60) return `${seconds} 秒前`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟前`;
  return `${Math.floor(seconds / 3600)} 小时前`;
}

export function createMockHash() {
  return `0x${Array.from(crypto.getRandomValues(new Uint8Array(32)), (byte) => byte.toString(16).padStart(2, '0')).join('')}`;
}
