import React, { useState } from 'react';
import { ExternalLink, Copy, Check, Coins, ArrowRightLeft } from 'lucide-react';
import { Token, NetworkConfig } from '../types';

interface AccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  userAddress: string;
  onUpdateAddress: (newAddr: string) => void;
  tokens: Record<string, Token>;
  onFaucet: (symbol: string, amount: number) => void;
  network: NetworkConfig;
}

export const AccountModal: React.FC<AccountModalProps> = ({
  isOpen,
  onClose,
  userAddress,
  onUpdateAddress,
  tokens,
  onFaucet,
  network,
}) => {
  const [copied, setCopied] = useState(false);
  const [customAddress, setCustomAddress] = useState(userAddress);
  const [faucetSuccess, setFaucetSuccess] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(userAddress);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const handleSwitch = (e: React.FormEvent) => {
    e.preventDefault();
    if (customAddress.trim()) {
      onUpdateAddress(customAddress.trim());
      onClose();
    }
  };

  const claimFaucet = (symbol: string, amt: number) => {
    onFaucet(symbol, amt);
    setFaucetSuccess(`成功申领 +${amt} ${symbol}!`);
    setTimeout(() => setFaucetSuccess(null), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
      <div className="bg-white rounded-xl shadow-2xl border border-slate-200 max-w-md w-full overflow-hidden flex flex-col">
        {/* Header */}
        <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-full bg-blue-600 text-white flex items-center justify-center font-bold text-xs">
              0x
            </div>
            <span className="font-bold text-slate-900 text-sm">本地钱包账户与资产管理</span>
          </div>
          <button
            onClick={onClose}
            className="w-7 h-7 rounded-md flex items-center justify-center text-slate-400 hover:text-slate-700 cursor-pointer"
          >
            ✕
          </button>
        </div>

        <div className="p-5 space-y-4 text-xs">
          {/* Address Card */}
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 space-y-2">
            <div className="text-slate-500 font-medium flex items-center justify-between">
              <span>当前连接地址 ({network.shortName})</span>
              <span className="text-emerald-600 font-medium">● 已授权</span>
            </div>
            <div className="font-mono text-slate-800 break-all bg-white p-2 rounded border border-slate-200 flex items-center justify-between gap-2">
              <span className="text-[11px]">{userAddress}</span>
              <div className="flex items-center gap-1 shrink-0">
                <button
                  onClick={handleCopy}
                  className="p-1 hover:text-blue-600 cursor-pointer"
                  title="复制"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
                <a
                  href={`${network.explorerUrl}/address/${userAddress}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="p-1 hover:text-blue-600"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            </div>
          </div>

          {/* Faucet Alert */}
          {faucetSuccess && (
            <div className="bg-emerald-50 text-emerald-800 border border-emerald-200 p-2 rounded text-center font-medium">
              {faucetSuccess}
            </div>
          )}

          {/* Quick Faucet test tokens */}
          <div className="space-y-1.5">
            <div className="flex items-center gap-1.5 text-slate-700 font-bold">
              <Coins className="w-3.5 h-3.5 text-amber-500" />
              <span>测试币水龙头 (Quick Faucet)</span>
            </div>
            <div className="grid grid-cols-3 gap-2 pt-1">
              <button
                onClick={() => claimFaucet('ETH', 5)}
                className="py-1.5 px-2 bg-slate-100 hover:bg-slate-200 rounded border border-slate-200 font-mono text-[11px] text-slate-800 transition-colors cursor-pointer"
              >
                +5 ETH
              </button>
              <button
                onClick={() => claimFaucet('WETH', 5)}
                className="py-1.5 px-2 bg-slate-100 hover:bg-slate-200 rounded border border-slate-200 font-mono text-[11px] text-slate-800 transition-colors cursor-pointer"
              >
                +5 WETH
              </button>
              <button
                onClick={() => claimFaucet('USDC', 10000)}
                className="py-1.5 px-2 bg-slate-100 hover:bg-slate-200 rounded border border-slate-200 font-mono text-[11px] text-slate-800 transition-colors cursor-pointer"
              >
                +10,000 USDC
              </button>
            </div>
          </div>

          {/* Switch Wallet Form */}
          <form onSubmit={handleSwitch} className="pt-2 border-t border-slate-100 space-y-2">
            <label className="block text-slate-600 font-medium">
              切换其他测试地址
            </label>
            <div className="flex gap-2">
              <input
                type="text"
                value={customAddress}
                onChange={(e) => setCustomAddress(e.target.value)}
                placeholder="0x..."
                className="flex-1 border border-slate-200 rounded-md px-3 py-1.5 font-mono text-xs focus:ring-1 focus:ring-blue-500 focus:outline-none"
              />
              <button
                type="submit"
                className="bg-slate-900 hover:bg-slate-800 text-white px-3 py-1.5 rounded-md font-medium text-xs cursor-pointer"
              >
                切换
              </button>
            </div>
          </form>
        </div>

        <div className="p-3 bg-slate-50 border-t border-slate-100 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-1.5 bg-slate-200 hover:bg-slate-300 text-slate-700 font-medium rounded-md text-xs cursor-pointer"
          >
            完成
          </button>
        </div>
      </div>
    </div>
  );
};
