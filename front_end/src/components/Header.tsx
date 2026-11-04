import React, { useMemo, useState } from 'react';
import { ConnectButton } from '@rainbow-me/rainbowkit';
import { useAccount, useSwitchChain } from 'wagmi';
import { Check, ChevronDown, LoaderCircle } from 'lucide-react';
import { isMockMode } from '../config/env';
import { isWagmiChain } from '../config/wagmi';
import { selectableNetworks } from '../mock/initialData';
import { NetworkConfig } from '../types';

interface HeaderProps {
  currentNetwork: NetworkConfig;
  onSelectNetwork: (net: NetworkConfig) => void;
  behindBlocks: number;
  confirmationTarget: number;
  isSyncing: boolean;
  onOpenAccountModal: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  currentNetwork,
  onSelectNetwork,
  behindBlocks,
  confirmationTarget,
  isSyncing,
  onOpenAccountModal,
}) => {
  const [networkDropdownOpen, setNetworkDropdownOpen] = useState(false);
  const [switching, setSwitching] = useState(false);
  const [switchError, setSwitchError] = useState<string | null>(null);
  const mock = isMockMode();
  const account = useAccount();
  const { switchChainAsync } = useSwitchChain();
  const networks = useMemo(() => selectableNetworks(!mock), [mock]);

  const handleSelectNetwork = async (net: NetworkConfig) => {
    setNetworkDropdownOpen(false);
    setSwitchError(null);
    onSelectNetwork(net);
    if (mock || !account.isConnected || !isWagmiChain(net.chainId)) {
      return;
    }
    if (account.chainId === net.chainId) {
      return;
    }
    setSwitching(true);
    try {
      await switchChainAsync({ chainId: net.chainId });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '钱包切链失败';
      setSwitchError(message.includes('User rejected') || message.includes('user rejected')
        ? '已取消切链，请在钱包中确认网络'
        : '钱包切链失败，请在钱包内手动切换');
    } finally {
      setSwitching(false);
    }
  };

  return (
    <header className="h-14 bg-white border-b border-slate-200 px-4 md:px-6 flex items-center justify-between shrink-0 z-30 select-none">
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-[#09182b] flex items-center justify-center shadow-xs border border-slate-800 shrink-0">
          <svg
            className="w-4 h-4 text-cyan-400"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="12" cy="5" r="3" />
            <line x1="12" y1="8" x2="12" y2="22" />
            <path d="M5 12H2a10 10 0 0 0 20 0h-3" />
          </svg>
        </div>

        <span className="text-lg font-bold text-slate-900 tracking-tight">
          Anchor
        </span>

        <span className="text-slate-300 font-light mx-0.5 hidden sm:inline">|</span>

        <span className="text-xs text-slate-500 font-normal tracking-wide hidden sm:inline">
          监控 Swap，核对余额，即时兑换
        </span>
      </div>

      <div className="flex items-center gap-3 sm:gap-5">
        <div className="flex items-center gap-1.5 text-xs text-slate-700 font-medium">
          <span className={`w-2 h-2 rounded-full ${isSyncing ? 'bg-emerald-500 animate-pulse' : 'bg-emerald-500'}`} />
          <span>{isSyncing ? 'Indexer 同步中' : 'Indexer 已暂停'}</span>
        </div>

        <div className="relative">
          <button
            onClick={() => setNetworkDropdownOpen(!networkDropdownOpen)}
            disabled={switching}
            className="flex items-center gap-2 bg-white hover:bg-slate-50 border border-slate-200 text-slate-800 text-xs font-semibold px-3 py-1.5 rounded-md shadow-xs transition-colors cursor-pointer disabled:opacity-60 disabled:cursor-wait"
            title="切换应用网络（已连接钱包时同步切链）"
          >
            {switching ? (
              <LoaderCircle className="w-3.5 h-3.5 text-slate-500 animate-spin" />
            ) : (
              <span className="w-1.5 h-1.5 rounded-full bg-[#0f1d33]" />
            )}
            <span>{currentNetwork.name}</span>
            <ChevronDown className={`w-3.5 h-3.5 text-slate-400 transition-transform ${networkDropdownOpen ? 'rotate-180' : ''}`} />
          </button>

          {switchError && (
            <div className="absolute right-0 top-full mt-1 z-50 max-w-[220px] rounded-md border border-amber-200 bg-amber-50 px-2.5 py-1.5 text-[10px] text-amber-800 shadow-sm">
              {switchError}
            </div>
          )}

          {networkDropdownOpen && (
            <>
              <div
                className="fixed inset-0 z-40"
                onClick={() => setNetworkDropdownOpen(false)}
              />
              <div className="absolute right-0 mt-1.5 w-56 bg-white border border-slate-200 rounded-lg shadow-lg z-50 overflow-hidden text-xs">
                <div className="px-3 py-2 border-b border-slate-100 bg-slate-50/80 text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                  选择网络
                </div>
                <div className="py-1">
                  {networks.map((net) => {
                    const active = net.id === currentNetwork.id;
                    const walletOnChain = !mock && account.isConnected && account.chainId === net.chainId;
                    return (
                      <button
                        key={net.id}
                        onClick={() => {
                          void handleSelectNetwork(net);
                        }}
                        className={`w-full text-left px-3 py-2.5 flex items-center gap-2.5 transition-colors ${
                          active
                            ? 'bg-slate-50 text-slate-900'
                            : 'text-slate-700 hover:bg-slate-50'
                        }`}
                      >
                        <span className={`w-2 h-2 rounded-full shrink-0 ${active ? 'bg-[#0f1d33]' : 'bg-slate-300'}`} />
                        <span className="flex-1 min-w-0">
                          <span className={`block truncate ${active ? 'font-semibold' : 'font-medium'}`}>{net.name}</span>
                          <span className="block text-[10px] text-slate-400 font-mono">chainId {net.chainId}</span>
                        </span>
                        {walletOnChain && (
                          <span className="text-[10px] font-semibold text-emerald-600 shrink-0">钱包已连接</span>
                        )}
                        {active && <Check className="w-3.5 h-3.5 text-[#0f1d33] shrink-0" />}
                      </button>
                    );
                  })}
                </div>
                {!mock && (
                  <div className="px-3 py-2 border-t border-slate-100 text-[10px] text-slate-400 leading-relaxed">
                    切换后将同步请求钱包切到同一网络
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        <div className="text-[11px] leading-tight text-slate-500 font-mono text-right hidden md:block">
          <div>落后块数 : <span className="font-semibold text-slate-700">{behindBlocks}</span></div>
          <div>确认数: <span className="font-semibold text-slate-700">{confirmationTarget}</span></div>
        </div>

        {mock ? (
          <button
            onClick={onOpenAccountModal}
            className="relative w-8 h-8 rounded-full overflow-hidden border border-slate-200 hover:ring-2 hover:ring-slate-900/10 transition-all cursor-pointer shrink-0 shadow-xs"
            title="查看当前账户信息"
          >
            <img
              src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80"
              alt="User avatar"
              referrerPolicy="no-referrer"
              className="w-full h-full object-cover"
              onError={(e) => {
                e.currentTarget.style.display = 'none';
                e.currentTarget.nextElementSibling?.classList.remove('hidden');
              }}
            />
            <div className="hidden w-full h-full bg-slate-800 text-white flex items-center justify-center font-bold text-xs">
              0x
            </div>
          </button>
        ) : (
          <div className="flex items-center gap-2">
            <ConnectButton
              chainStatus="none"
              accountStatus="address"
              showBalance={false}
            />
            <button
              onClick={onOpenAccountModal}
              className="text-[11px] text-slate-500 hover:text-slate-800 underline-offset-2 hover:underline cursor-pointer"
              title="账户详情"
            >
              账户
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
