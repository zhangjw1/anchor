import React, { useState } from 'react';
import { Plus, Check, Copy, Trash2, BookmarkCheck, ExternalLink, Filter } from 'lucide-react';
import { WatchAddress, SwapTransaction } from '../types';
import { formatRelativeTime } from '../utils/swap';

interface WatchlistPanelProps {
  watchlist: WatchAddress[];
  swaps: SwapTransaction[];
  selectedAddressId: string | null;
  onSelectAddress: (id: string | null) => void;
  onAddAddress: (address: string, name?: string, tag?: WatchAddress['tag']) => Promise<void>;
  onDeleteAddress: (id: string) => Promise<void>;
  explorerUrl: string;
}

export const WatchlistPanel: React.FC<WatchlistPanelProps> = ({
  watchlist,
  swaps,
  selectedAddressId,
  onSelectAddress,
  onAddAddress,
  onDeleteAddress,
  explorerUrl,
}) => {
  const [inputVal, setInputVal] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [customName, setCustomName] = useState('');
  const [inputError, setInputError] = useState('');
  const validateAddress = (value: string) => {
    const error = !/^0x[0-9a-fA-F]{40}$/.test(value) ? '请输入有效的 0x 钱包地址' : watchlist.some((item) => item.address.toLowerCase() === value.toLowerCase()) ? '该地址已在监控列表中' : '';
    setInputError(error);
    return !error;
  };
  const [customTag, setCustomTag] = useState<WatchAddress['tag']>('WATCH');

  const handleQuickAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    const clean = inputVal.trim();
    if (!validateAddress(clean)) return;
    try {
      await onAddAddress(clean);
      setInputVal('');
      setInputError('');
    } catch (error) {
      setInputError(error instanceof Error ? error.message : '添加失败');
    }
  };

  const handleModalAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    const clean = inputVal.trim();
    if (!validateAddress(clean)) return;
    try {
      await onAddAddress(clean, customName.trim() || undefined, customTag);
      setInputVal('');
      setCustomName('');
      setInputError('');
      setIsAddModalOpen(false);
    } catch (error) {
      setInputError(error instanceof Error ? error.message : '添加失败');
    }
  };

  const copyToClipboard = (addr: string, id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(addr);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const formatAddress = (addr: string) => {
    if (addr.length <= 12) return addr;
    return `${addr.slice(0, 6)}...${addr.slice(-4)}`;
  };

  return (
    <div className="flex flex-col w-full min-w-0 h-full bg-white">
      {/* Header */}
      <div className="px-3.5 py-3 border-b border-slate-100 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          {/* Watchlist ID icon */}
          <div className="text-slate-800">
            <svg
              className="w-3.5 h-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <rect x="3" y="4" width="18" height="16" rx="2" />
              <circle cx="9" cy="10" r="2" />
              <line x1="15" y1="9" x2="17" y2="9" />
              <line x1="15" y1="13" x2="17" y2="13" />
              <path d="M7 16a2 2 0 0 1 4 0" />
            </svg>
          </div>
          <span className="text-[11px] font-bold tracking-wider text-slate-800 uppercase">
            WATCHLIST 地址簿
          </span>
        </div>

        <button
          onClick={() => setIsAddModalOpen(true)}
          className="w-5 h-5 rounded flex items-center justify-center text-slate-400 hover:bg-slate-100 hover:text-slate-900 transition-colors cursor-pointer"
          title="添加新监控项"
        >
          <Plus className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Filter notification if an address is currently filtering the stream */}
      {selectedAddressId && (
        <div className="px-3 py-1.5 bg-blue-50/70 border-b border-blue-100 flex items-center justify-between text-[11px] text-blue-700 font-medium shrink-0">
          <div className="flex items-center gap-1 truncate">
            <Filter className="w-3 h-3 shrink-0" />
            <span className="truncate">已过滤监控地址</span>
          </div>
          <button
            onClick={() => onSelectAddress(null)}
            className="text-blue-600 hover:text-blue-900 underline text-[10px] shrink-0 ml-2 cursor-pointer"
          >
            清除过滤
          </button>
        </div>
      )}

      {/* Address List */}
      <div className="flex-1 overflow-y-auto divide-y divide-slate-100">
        {watchlist.length === 0 ? (
          <div className="p-6 text-center text-xs text-slate-400">
            暂无监控地址，请在下方添加
          </div>
        ) : (
          watchlist.map((item) => {
            const isSelected = selectedAddressId === item.id;
            const address = item.address.toLowerCase();
            const activity = swaps.filter((tx) => tx.senderAddress.toLowerCase() === address || tx.recipientAddress.toLowerCase() === address);
            const swapCount = item.swapCount ?? activity.length;
            const lastActive = item.lastActiveAt ?? (activity.length ? Math.max(...activity.map((tx) => tx.timestamp)) : null);
            return (
              <div
                key={item.id}
                onClick={() => onSelectAddress(isSelected ? null : item.id)}
                className={`py-2.5 px-3 transition-colors cursor-pointer group relative ${
                  isSelected ? 'bg-blue-50/40' : 'hover:bg-slate-50/80'
                }`}
              >
                {/* Top Row: Name and Tag */}
                <div className="flex items-center justify-between mb-0.5">
                  <span className="text-[12px] font-bold text-slate-900 tracking-tight leading-tight group-hover:text-blue-600 transition-colors">
                    {item.name}
                  </span>
                  
                  {/* Tag Pill */}
                  {item.tag === 'WATCH' ? (
                    <span className="bg-[#0f1d33] text-white text-[9px] font-bold px-1.5 py-0.5 rounded tracking-wider uppercase">
                      WATCH
                    </span>
                  ) : item.tag === 'ROUTER' ? (
                    <span className="bg-slate-100 text-slate-600 border border-slate-200 text-[9px] font-bold px-1.5 py-0.5 rounded tracking-wider uppercase">
                      ROUTER
                    </span>
                  ) : item.tag === 'MEV' ? (
                    <span className="bg-amber-100 text-amber-800 border border-amber-200 text-[9px] font-bold px-1.5 py-0.5 rounded tracking-wider uppercase">
                      MEV
                    </span>
                  ) : (
                    <span className="bg-slate-800 text-white text-[9px] font-bold px-1.5 py-0.5 rounded tracking-wider uppercase">
                      {item.tag}
                    </span>
                  )}
                </div>

                <div className="text-[10px] text-slate-500 mt-1 mb-1 space-y-0.5">
                  <div>Swap 次数 <span className="font-mono font-semibold text-slate-700">{swapCount}</span></div>
                  <div>最近活跃时间 <span>{lastActive ? formatRelativeTime(lastActive) : '暂无交易'}</span></div>
                </div>
                {/* Bottom Row: Address and Quick Actions */}
                <div className="flex items-center justify-between">
                  <div className="text-[10.5px] text-slate-400 font-mono tracking-tight">
                    {formatAddress(item.address)}
                  </div>

                  <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={(e) => copyToClipboard(item.address, item.id, e)}
                      title="复制完整地址"
                      className="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 rounded cursor-pointer"
                    >
                      {copiedId === item.id ? (
                        <Check className="w-3 h-3 text-emerald-600" />
                      ) : (
                        <Copy className="w-3 h-3" />
                      )}
                    </button>
                    <a
                      href={`${explorerUrl}/address/${item.address}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      title="在区块浏览器查看"
                      className="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 rounded"
                    >
                      <ExternalLink className="w-3 h-3" />
                    </a>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onDeleteAddress(item.id).catch(() => undefined);
                      }}
                      title="删除该监控"
                      className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded cursor-pointer"
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Bottom Input Area */}
      <div className="p-3 border-t border-slate-200 bg-white shrink-0">
        <form onSubmit={handleQuickAdd} className="space-y-2">
          {inputError && <p role="alert" className="text-xs text-rose-600">{inputError}</p>}
          <input
            type="text"
            value={inputVal}
            onChange={(e) => setInputVal(e.target.value)}
            placeholder="粘贴 0x 地址..."
            className="w-full bg-white border border-slate-200 rounded-md px-2.5 py-1.5 text-[11px] text-slate-800 placeholder:text-slate-400 font-mono focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500 transition-all"
          />
          <button
            type="submit"
            disabled={!inputVal.trim()}
            className="w-full bg-[#0c1a30] hover:bg-[#152a4a] disabled:bg-slate-200 disabled:text-slate-400 text-white font-medium text-xs py-2 px-3 rounded-md transition-colors flex items-center justify-center gap-1.5 cursor-pointer disabled:cursor-not-allowed shadow-xs"
          >
            添加监控地址
          </button>
        </form>
      </div>

      {/* Detailed Add Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-xl shadow-2xl border border-slate-200 max-w-sm w-full p-5 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
              <h3 className="font-bold text-slate-900 text-sm">添加新的链上监控地址</h3>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 text-sm font-semibold"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleModalAdd} className="space-y-3 text-xs">
              {inputError && <p role="alert" className="text-rose-600">{inputError}</p>}
              <div>
                <label className="block text-slate-600 font-medium mb-1">地址 (以 0x 开头)</label>
                <input
                  type="text"
                  required
                  placeholder="0x..."
                  value={inputVal}
                  onChange={(e) => setInputVal(e.target.value)}
                  className="w-full border border-slate-200 rounded-md px-3 py-2 font-mono text-xs focus:ring-1 focus:ring-blue-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-600 font-medium mb-1">备注名称</label>
                <input
                  type="text"
                  placeholder="例如: Smart Whale #2 或 Arbitrage Bot"
                  value={customName}
                  onChange={(e) => setCustomName(e.target.value)}
                  className="w-full border border-slate-200 rounded-md px-3 py-2 text-xs focus:ring-1 focus:ring-blue-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-600 font-medium mb-1">类型标签</label>
                <div className="grid grid-cols-4 gap-1.5">
                  {(['WATCH', 'ROUTER', 'MEV', 'WHALE'] as const).map((tag) => (
                    <button
                      type="button"
                      key={tag}
                      onClick={() => setCustomTag(tag)}
                      className={`py-1.5 px-2 rounded text-center font-bold text-[10px] border transition-colors cursor-pointer ${
                        customTag === tag
                          ? 'border-blue-600 bg-blue-50 text-blue-700'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                      }`}
                    >
                      {tag}
                    </button>
                  ))}
                </div>
              </div>

              <div className="pt-2 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-3 py-2 rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={!inputVal.trim()}
                  className="px-4 py-2 rounded-md bg-[#0c1a30] text-white font-medium hover:bg-[#152a4a] disabled:opacity-50"
                >
                  确认添加
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
