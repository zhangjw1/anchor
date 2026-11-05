export type AddressTag = 'WATCH' | 'ROUTER' | 'MEV' | 'WHALE' | 'KOL';
export type SwapDirection = 'BUY' | 'SELL' | 'SWAP';
export type ConfirmStatus = 'CONFIRMING' | 'CONFIRMED' | 'FINALIZED' | 'REORGED';

export interface WatchAddressDto {
  id: string;
  chainId: number;
  address: string;
  name: string | null;
  tag: AddressTag;
  note: string | null;
  swapCount: number;
  lastActiveAt: number | null;
  createdAt: number;
}

export interface TokenAmountDto {
  address: string;
  symbol: string | null;
  decimals: number | null;
  amount: string;
}

export interface SwapHopDto {
  hopIndex: number;
  dex: string;
  pool: string;
  tokenIn: TokenAmountDto;
  tokenOut: TokenAmountDto;
  logIndex: number;
}

export interface SwapDto {
  id: string;
  chainId: number;
  txHash: string;
  logIndex: number;
  blockNumber: number;
  timestamp: number;
  direction: SwapDirection;
  dex: string;
  aggregator?: string | null;
  tokenIn: TokenAmountDto;
  tokenOut: TokenAmountDto;
  confirmStatus: ConfirmStatus;
  /** 相对当前头块的确认数；CONFIRMING 时用于进度条 */
  confirmations?: number;
  watchedAddress: string;
  hops?: SwapHopDto[];
}

export interface SwapPageDto {
  items: SwapDto[];
  page: number;
  size: number;
  total: number;
  hasMore?: boolean;
  nextBeforeBlock?: number | null;
  nextBeforeLogIndex?: number | null;
}

export class ApiError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const body = text ? JSON.parse(text) as { message?: string } : {};
  if (!response.ok) {
    throw new ApiError(body.message || `请求失败 ${response.status}`);
  }
  return body as T;
}

export function listWatchAddresses(chainId: number) {
  return request<WatchAddressDto[]>(`/api/v1/watch-addresses?chainId=${chainId}`);
}

export function createWatchAddress(input: { chainId: number; address: string; name?: string; tag: AddressTag; note?: string }) {
  return request<WatchAddressDto>('/api/v1/watch-addresses', { method: 'POST', body: JSON.stringify(input) });
}

export function deleteWatchAddress(id: string) {
  return request<void>(`/api/v1/watch-addresses/${id}`, { method: 'DELETE' });
}

export function listSwaps(chainId: number, address?: string) {
  const params = new URLSearchParams({ chainId: String(chainId), page: '1', size: '50' });
  if (address) {
    params.set('address', address);
  }
  return request<SwapPageDto>(`/api/v1/swaps?${params.toString()}`);
}

export interface ChainMetricsDto {
  chainId: number;
  headBlock: number;
  processedBlock: number;
  lagBlocks: number;
  rpcQps: number;
  rpcErrorRate: number;
  kafkaLag: number | null;
  kafkaStale?: boolean;
  outboxPending?: number;
  reorgCount: number;
  reorgDepth: number;
}

/** WS 不可用时轮询状态栏指标。 */
export function fetchChainMetrics(chainId: number) {
  return request<ChainMetricsDto>(`/api/v1/chains/${chainId}/metrics`);
}

export interface AssetHoldingDto {
  token: TokenAmountDto;
  balance: string;
  priceUsd: string | null;
  valueUsd: string | null;
}

export interface AssetPortfolioDto {
  chainId: number;
  address: string;
  holdings: AssetHoldingDto[];
  netWorthUsd: string | null;
  asOfMs: number;
}

export interface QuoteRequestDto {
  chainId: number;
  tokenIn: string;
  tokenOut: string;
  amountIn: string;
  slippageBps: number;
}

export interface QuoteDto {
  quoteId: string;
  chainId: number;
  tokenIn: TokenAmountDto;
  tokenOut: TokenAmountDto;
  amountIn: string;
  amountOut: string;
  amountOutMinimum: string;
  fee: number;
  priceImpactBps: number;
  gasEstimate: string;
  l1DataFeeWei: string;
  expiresAtMs: number;
}

export function getAssets(chainId: number, address: string, tokens?: string[]) {
  const params = new URLSearchParams({ chainId: String(chainId), address });
  tokens?.forEach((token) => params.append('tokens', token));
  return request<AssetPortfolioDto>(`/api/v1/assets?${params.toString()}`);
}

export function createQuote(body: QuoteRequestDto) {
  return request<QuoteDto>('/api/v1/quotes', { method: 'POST', body: JSON.stringify(body) });
}

export interface UnsignedTxDto {
  chainId: number;
  to: string;
  data: string;
  value: string;
  gas?: string | null;
}

export interface TradePrepareRequestDto {
  quoteId: string;
  userAddress: string;
  infiniteApprove?: boolean;
}

export interface TradePrepareResponseDto {
  quoteId: string;
  expiresAtMs: number;
  needsApprove: boolean;
  approveTx?: UnsignedTxDto | null;
  swapTx: UnsignedTxDto;
  amountOutMinimum: string;
  deadline: number;
}

export interface TradeReportRequestDto {
  chainId: number;
  txHash: string;
  kind: 'APPROVE' | 'SWAP';
  quoteId?: string | null;
  userAddress: string;
}

export interface TradeStatusDto {
  chainId: number;
  txHash: string;
  kind: 'APPROVE' | 'SWAP';
  status: 'PENDING' | 'CONFIRMED' | 'FAILED' | 'REPLACED';
  quoteId?: string | null;
  userAddress?: string | null;
  revertReason?: string | null;
  updatedAtMs: number;
}

export function prepareTrade(body: TradePrepareRequestDto) {
  return request<TradePrepareResponseDto>('/api/v1/trades/prepare', { method: 'POST', body: JSON.stringify(body) });
}

export function reportTrade(body: TradeReportRequestDto) {
  return request<TradeStatusDto>('/api/v1/trades/report', { method: 'POST', body: JSON.stringify(body) });
}

export function getTrade(chainId: number, txHash: string) {
  return request<TradeStatusDto>(`/api/v1/trades/${txHash}?chainId=${chainId}`);
}
