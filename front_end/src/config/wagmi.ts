import { connectorsForWallets } from '@rainbow-me/rainbowkit';
import {
  metaMaskWallet,
  rainbowWallet,
  walletConnectWallet,
} from '@rainbow-me/rainbowkit/wallets';
import { createConfig, http } from 'wagmi';
import { base, baseSepolia } from 'wagmi/chains';
import { walletConnectProjectId } from './env';

const projectId = walletConnectProjectId();

const connectors = connectorsForWallets(
  [
    {
      groupName: 'Recommended',
      wallets: [metaMaskWallet, rainbowWallet, walletConnectWallet],
    },
  ],
  {
    appName: 'Anchor',
    projectId,
  },
);

/** 钱包可切换的链；与 NETWORKS 中 live 模式可选列表对齐。 */
export const WAGMI_CHAIN_IDS = [base.id, baseSepolia.id] as const;

export function isWagmiChain(chainId: number): boolean {
  return (WAGMI_CHAIN_IDS as readonly number[]).includes(chainId);
}

export const wagmiConfig = createConfig({
  connectors,
  chains: [base, baseSepolia],
  transports: {
    [base.id]: http(),
    [baseSepolia.id]: http(),
  },
  ssr: false,
});
