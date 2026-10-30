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
  watchedAddress: string;
  hops?: SwapHopDto[];
}

export interface SwapPageDto {
  items: SwapDto[];
  page: number;
  size: number;
  total: number;
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
