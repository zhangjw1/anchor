/** 为 true 时使用本地 mock 余额/报价/钱包；为 false 时走真实 API + wagmi。 */
export function isMockMode(): boolean {
  const value = import.meta.env.VITE_USE_MOCK;
  if (value === undefined || value === '') {
    return true;
  }
  return value !== 'false' && value !== '0';
}

export function walletConnectProjectId(): string {
  return import.meta.env.VITE_WC_PROJECT_ID || '00000000000000000000000000000000';
}

export function defaultChainId(): number {
  const raw = import.meta.env.VITE_DEFAULT_CHAIN_ID;
  const parsed = raw ? Number(raw) : 8453;
  return Number.isFinite(parsed) ? parsed : 8453;
}

/** P5a 允许真实兑换的链（Base Sepolia）。 */
export const TRADE_ALLOWED_CHAIN_ID = 84532;
