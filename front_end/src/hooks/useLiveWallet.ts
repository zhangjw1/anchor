import { useAccount } from 'wagmi';
import { isMockMode } from '../config/env';

/** mock 模式下忽略链上连接状态，由 App 使用本地测试地址。 */
export function useLiveWallet() {
  const account = useAccount();
  if (isMockMode()) {
    return { address: undefined as `0x${string}` | undefined, isConnected: false, status: 'mock' as const };
  }
  return {
    address: account.address,
    isConnected: account.isConnected,
    status: account.status,
  };
}
