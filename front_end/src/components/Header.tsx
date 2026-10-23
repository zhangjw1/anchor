import React, { useState } from 'react';
import { ChevronDown, Check, Wifi, Globe, ShieldCheck } from 'lucide-react';
import { NETWORKS } from '../mock/initialData';
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

  return (
    <header className="h-14 bg-white border-b border-slate-200 px-4 md:px-6 flex items-center justify-between shrink-0 z-30 select-none">
      {/* Brand & Tagline Zone */}
      <div className="flex items-center gap-3">
        {/* Anchor Mark */}
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

        {/* Wordmark */}
        <span className="text-lg font-bold text-slate-900 tracking-tight">
          Anchor
        </span>

        {/* Vertical Separator */}
        <span className="text-slate-300 font-light mx-0.5 hidden sm:inline">|</span>

        {/* Tagline */}
        <span className="text-xs text-slate-500 font-normal tracking-wide hidden sm:inline">
          监控 Swap，核对余额，即时兑换
        </span>
      </div>

      {/* Right Controls & Telemetry */}
      <div className="flex items-center gap-4 sm:gap-6">
        {/* Indexer Status */}
        <div className="flex items-center gap-1.5 text-xs text-slate-700 font-medium">
          <span className={`w-2 h-2 rounded-full ${isSyncing ? 'bg-emerald-500 animate-pulse' : 'bg-emerald-500'}`} />
          <span>Indexer 同步中</span>
        </div>

        {/* Network Selector Dropdown */}
        <div className="relative">
          <button
            onClick={() => setNetworkDropdownOpen(!networkDropdownOpen)}
            className="flex items-center gap-2 bg-white hover:bg-slate-50 border border-slate-200 text-slate-800 text-xs font-semibold px-3 py-1.5 rounded-md shadow-xs transition-colors cursor-pointer"
          >
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500"></span>
            <span>{currentNetwork.name}</span>
            <ChevronDown className={`w-3.5 h-3.5 text-slate-400 transition-transform ${networkDropdownOpen ? 'rotate-180' : ''}`} />
          </button>

          {networkDropdownOpen && (
            <>
              <div 
                className="fixed inset-0 z-40" 
                onClick={() => setNetworkDropdownOpen(false)} 
              />
              <div className="absolute right-0 mt-1.5 w-48 bg-white border border-slate-200 rounded-lg shadow-lg z-50 py-1 text-xs">
                <div className="px-3 py-1.5 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                  选择区块链网络
                </div>
                {NETWORKS.map((net) => (
                  <button
                    key={net.id}
                    onClick={() => {
                      onSelectNetwork(net);
                      setNetworkDropdownOpen(false);
                    }}
                    className={`w-full text-left px-3 py-2 flex items-center justify-between hover:bg-slate-50 transition-colors ${
                      net.id === currentNetwork.id ? 'font-semibold text-blue-600 bg-blue-50/50' : 'text-slate-700'
                    }`}
                  >
                    <span>{net.name}</span>
                    {net.id === currentNetwork.id && <Check className="w-3.5 h-3.5 text-blue-600" />}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>

        {/* Sync telemetry text */}
        <div className="text-[11px] leading-tight text-slate-500 font-mono text-right hidden md:block">
          <div>落后块数 : <span className="font-semibold text-slate-700">{behindBlocks}</span></div>
          <div>确认数: <span className="font-semibold text-slate-700">{confirmationTarget}</span></div>
        </div>

        {/* User Avatar */}
        <button
          onClick={onOpenAccountModal}
          className="relative w-8 h-8 rounded-full overflow-hidden border border-slate-200 hover:ring-2 hover:ring-blue-500/20 transition-all cursor-pointer shrink-0 shadow-xs"
          title="查看当前账户信息"
        >
          {/* Avatar image matching the screenshot */}
          <img
            src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80"
            alt="User avatar"
            referrerPolicy="no-referrer"
            className="w-full h-full object-cover"
            onError={(e) => {
              // Fallback to handsome SVG portrait if image doesn't load
              e.currentTarget.style.display = 'none';
              e.currentTarget.nextElementSibling?.classList.remove('hidden');
            }}
          />
          <div className="hidden w-full h-full bg-slate-800 text-white flex items-center justify-center font-bold text-xs">
            0x
          </div>
        </button>
      </div>
    </header>
  );
};
