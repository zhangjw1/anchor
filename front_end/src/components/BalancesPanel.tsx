import React, { useState } from 'react';
import { Copy, Check } from 'lucide-react';
import { Token } from '../types';
import { WALLET_SYMBOLS } from '../utils/swap';

interface BalancesPanelProps {
  userAddress: string;
  tokens: Record<string, Token>;
  onRefreshBalances?: () => void;
  explorerUrl: string;
}

export const BalancesPanel: React.FC<BalancesPanelProps> = ({
  userAddress,
  tokens,
  onRefreshBalances,
  explorerUrl,
}) => {
  const [copied, setCopied] = useState(false);
  const [showAllTokens, setShowAllTokens] = useState(false);

  // Calculate Net Worth: sum of all token balances * price
  const totalNetWorth = Object.values(tokens).reduce((acc, t) => {
    return acc + t.balance * t.priceUsd;
  }, 0);

  const copyAddress = () => {
    navigator.clipboard.writeText(userAddress);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const formatShortAddress = (addr: string) => {
    if (addr.length <= 10) return addr;
    return `${addr.slice(0, 4)}...${addr.slice(-4)}`;
  };

  return (
    <div className="bg-white border-b border-slate-200 p-4">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <span className="text-xs font-bold tracking-wider text-slate-800 uppercase">
          我的钱包
        </span>

        {/* Address badge */}
        <button
          onClick={copyAddress}
          className="text-xs font-mono text-slate-500 hover:text-slate-900 flex items-center gap-1 cursor-pointer py-0.5 px-1.5 rounded hover:bg-slate-50 transition-colors"
          title="点击复制钱包地址"
        >
          <span>{formatShortAddress(userAddress)}</span>
          {copied ? (
            <Check className="w-3 h-3 text-emerald-600" />
          ) : (
            <Copy className="w-3 h-3 text-slate-400" />
          )}
        </button>
      </div>

      {/* Primary Token Balances Row */}
      <div className="grid grid-cols-3 gap-2 pb-4 border-b border-slate-100">
        {WALLET_SYMBOLS.map((symbol) => <div key={symbol} className="min-w-0">
          <div className="text-[11px] font-semibold text-slate-500 tracking-wider mb-1">{symbol}</div>
          <div data-testid={`wallet-${symbol}`} className="text-sm font-bold font-mono text-slate-900 tracking-tight">
            {(tokens[symbol]?.balance ?? 0).toLocaleString('en-US', { minimumFractionDigits: symbol === 'USDC' ? 2 : 3, maximumFractionDigits: symbol === 'USDC' ? 2 : 3 })}
          </div>
        </div>)}
      </div>

      {/* Expanded token rows toggle */}
      {showAllTokens && (
        <div className="py-2.5 space-y-2 border-b border-slate-100 text-xs font-mono">
          {Object.values(tokens)
            .filter((t) => !WALLET_SYMBOLS.some((symbol) => symbol === t.symbol) && t.balance > 0)
            .map((token) => (
              <div key={token.symbol} className="flex items-center justify-between">
                <span className="text-slate-500">{token.symbol}</span>
                <span className="font-semibold text-slate-800">
                  {token.balance.toLocaleString()}
                </span>
              </div>
            ))}
        </div>
      )}

      {/* Total Net Worth Row */}
      <div className="flex items-center justify-between pt-3">
        <button
          onClick={() => setShowAllTokens(!showAllTokens)}
          aria-expanded={showAllTokens}
          title="展开其他资产"
          className="text-xs font-bold text-slate-500 uppercase tracking-wider hover:text-slate-800 transition-colors cursor-pointer flex items-center gap-1"
        >
          <span>TOTAL NET WORTH</span>
        </button>

        <div data-testid="net-worth" className="text-lg font-bold font-mono text-slate-900 tracking-tight">
          ${totalNetWorth.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </div>
      </div>
    </div>
  );
};
