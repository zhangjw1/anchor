import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RainbowKitProvider, lightTheme } from '@rainbow-me/rainbowkit';
import { WagmiProvider } from 'wagmi';
import { ReactNode, useState } from 'react';
import { wagmiConfig } from '../config/wagmi';
import '@rainbow-me/rainbowkit/styles.css';

const anchorRainbowTheme = (() => {
  const theme = lightTheme({
    accentColor: '#0f1d33',
    accentColorForeground: '#ffffff',
    borderRadius: 'medium',
    fontStack: 'system',
    overlayBlur: 'small',
  });
  theme.colors.modalBackground = '#ffffff';
  theme.colors.modalBorder = '#e2e8f0';
  theme.colors.modalText = '#0f172a';
  theme.colors.modalTextSecondary = '#64748b';
  theme.colors.profileForeground = '#f8fafc';
  theme.colors.closeButton = '#94a3b8';
  theme.colors.closeButtonBackground = '#f1f5f9';
  theme.colors.menuItemBackground = '#f8fafc';
  theme.colors.actionButtonBorder = '#e2e8f0';
  theme.shadows.dialog = '0 12px 32px rgba(15, 23, 42, 0.12)';
  return theme;
})();

/** 始终挂上 wagmi，便于 hooks 稳定；mock 模式由业务层决定是否用真实钱包。 */
export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <WagmiProvider config={wagmiConfig}>
      <QueryClientProvider client={queryClient}>
        <RainbowKitProvider theme={anchorRainbowTheme} modalSize="compact">
          {children}
        </RainbowKitProvider>
      </QueryClientProvider>
    </WagmiProvider>
  );
}
