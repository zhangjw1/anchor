import React, { useState } from 'react';
import { 
  RotateCw, 
  Filter, 
  ChevronDown, 
  ArrowRight, 
  Layers, 
  AlertTriangle,
  Play,
  Pause,
  SlidersHorizontal,
  ExternalLink,
  Check
} from 'lucide-react';
import { SwapTransaction, SwapDirection } from '../types';
import { formatAmount, formatRelativeTime, FEED_LIMIT } from '../utils/swap';
import { TokenPair } from './TokenPair';

interface RecentSwapsPanelProps {
  swaps: SwapTransaction[];
  onSelectTx: (tx: SwapTransaction) => void;
  latestBlock: number;
  isStreaming: boolean;
  onToggleStreaming: () => void;
  onTriggerReorgSimulation: () => void;
  explorerUrl: string;
}

export const RecentSwapsPanel: React.FC<RecentSwapsPanelProps> = ({
  swaps,
  onSelectTx,
  latestBlock,
  isStreaming,
  onToggleStreaming,
  onTriggerReorgSimulation,
  explorerUrl,
}) => {
  const [selectedDex, setSelectedDex] = useState<string>('ALL');
  const [selectedDirection, setSelectedDirection] = useState<'ALL' | SwapDirection>('ALL');
  const [dexDropdownOpen, setDexDropdownOpen] = useState(false);
  const [directionDropdownOpen, setDirectionDropdownOpen] = useState(false);
  const [showOnlyWatched, setShowOnlyWatched] = useState(false);
  const limitCount = FEED_LIMIT;

  // Available DEXes
  const dexList = ['ALL', 'Uniswap V3', '1inch', 'Aerodrome', 'Curve', 'SushiSwap'];

  // Filtered swaps
  const filteredSwaps = swaps.filter((tx) => {
    if (selectedDex !== 'ALL' && tx.dex !== selectedDex) return false;
    if (selectedDirection !== 'ALL' && tx.direction !== selectedDirection) return false;
    if (showOnlyWatched && !tx.watchedAddressMatch) return false;
    return true;
  }).slice(0, limitCount);

  const formatHash = (h: string) => {
    if (!h) return '';
    return `${h.slice(0, 6)}...${h.slice(-4)}`;
  };

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Header */}
      <div className="p-4 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3 shrink-0">
        <div className="flex items-center gap-2">
          {/* Refresh / Play-Pause Icon */}
          <button
            onClick={onToggleStreaming}
            title={isStreaming ? '暂停实时推送' : '恢复实时推送'}
            className="flex items-center gap-1.5 text-xs font-bold tracking-wider text-slate-800 uppercase hover:text-blue-600 transition-colors cursor-pointer group"
          >
            <RotateCw className={`w-3.5 h-3.5 text-slate-700 group-hover:text-blue-600 ${isStreaming ? 'animate-spin-slow' : ''}`} />
            <span>RECENT SWAPS</span>
          </button>
          
          <span className="text-xs text-slate-400 font-normal hidden sm:inline">
            监控地址成交 · 非本机发送
          </span>
        </div>

        {/* Action & Filter Controls */}
        <div className="flex items-center gap-2 text-xs">
          {/* DEX Filter Dropdown */}
          <div className="relative">
            <button
              onClick={() => {
                setDexDropdownOpen(!dexDropdownOpen);
                setDirectionDropdownOpen(false);
              }}
              className={`px-2.5 py-1.5 border rounded-md font-medium flex items-center gap-1 transition-colors cursor-pointer ${
                selectedDex !== 'ALL'
                  ? 'border-blue-500 bg-blue-50/50 text-blue-700'
                  : 'border-slate-200 text-slate-700 hover:bg-slate-50'
              }`}
            >
              <span>{selectedDex === 'ALL' ? 'DEX 过滤' : selectedDex}</span>
              <ChevronDown className="w-3 h-3 text-slate-400" />
            </button>

            {dexDropdownOpen && (
              <>
                <div 
                  className="fixed inset-0 z-40" 
                  onClick={() => setDexDropdownOpen(false)} 
                />
                <div className="absolute right-0 mt-1 w-36 bg-white border border-slate-200 rounded-md shadow-lg z-50 py-1">
                  {dexList.map((dex) => (
                    <button
                      key={dex}
                      onClick={() => {
                        setSelectedDex(dex);
                        setDexDropdownOpen(false);
                      }}
                      className="w-full text-left px-3 py-1.5 hover:bg-slate-50 flex items-center justify-between text-xs text-slate-700"
                    >
                      <span>{dex === 'ALL' ? '全部 DEX' : dex}</span>
                      {selectedDex === dex && <Check className="w-3 h-3 text-blue-600" />}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>

          {/* Direction Filter Dropdown */}
          <div className="relative">
            <button
              onClick={() => {
                setDirectionDropdownOpen(!directionDropdownOpen);
                setDexDropdownOpen(false);
              }}
              className={`px-2.5 py-1.5 border rounded-md font-medium flex items-center gap-1 transition-colors cursor-pointer ${
                selectedDirection !== 'ALL'
                  ? 'border-blue-500 bg-blue-50/50 text-blue-700'
                  : 'border-slate-200 text-slate-700 hover:bg-slate-50'
              }`}
            >
              <span>{selectedDirection === 'ALL' ? '方向' : selectedDirection}</span>
              <ChevronDown className="w-3 h-3 text-slate-400" />
            </button>

            {directionDropdownOpen && (
              <>
                <div 
                  className="fixed inset-0 z-40" 
                  onClick={() => setDirectionDropdownOpen(false)} 
                />
                <div className="absolute right-0 mt-1 w-28 bg-white border border-slate-200 rounded-md shadow-lg z-50 py-1">
                  {(['ALL', 'BUY', 'SELL'] as const).map((dir) => (
                    <button
                      key={dir}
                      onClick={() => {
                        setSelectedDirection(dir);
                        setDirectionDropdownOpen(false);
                      }}
                      className="w-full text-left px-3 py-1.5 hover:bg-slate-50 flex items-center justify-between text-xs text-slate-700"
                    >
                      <span>{dir === 'ALL' ? '全部方向' : dir}</span>
                      {selectedDirection === dir && <Check className="w-3 h-3 text-blue-600" />}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>

          {/* Filter button */}
          <button
            onClick={() => setShowOnlyWatched(!showOnlyWatched)}
            title={showOnlyWatched ? '当前仅显示监控地址，点击显示全部' : '仅显示监控地址'}
            className={`p-1.5 border rounded-md transition-colors cursor-pointer ${
              showOnlyWatched
                ? 'border-blue-500 bg-blue-500 text-white'
                : 'border-slate-200 text-slate-500 hover:bg-slate-50 hover:text-slate-800'
            }`}
          >
            <Filter className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Table container */}
      <div className="flex-1 overflow-x-auto overflow-y-auto">
        <table className="w-full text-left border-collapse text-xs">
          <thead>
            <tr className="border-b border-slate-100 text-[11px] font-semibold text-slate-400 select-none">
              <th className="py-2.5 px-4 font-medium w-16">方向</th>
              <th className="py-2.5 px-4 font-medium">交易对</th>
              <th className="py-2.5 px-4 font-medium">DEX</th>
              <th className="py-2.5 px-4 font-medium text-right">数量</th>
              <th className="py-2.5 px-4 font-medium pl-6">状态</th>
              <th className="py-2.5 px-4 font-medium font-mono">TX HASH</th>
              <th className="py-2.5 px-4 font-medium text-right">时间</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filteredSwaps.length === 0 ? (
              <tr>
                <td colSpan={7} className="text-center py-12 text-slate-400">
                  没有符合当前过滤条件的交易
                </td>
              </tr>
            ) : (
              filteredSwaps.map((tx) => {
                const isReorg = tx.status.type === 'reorg';
                const isConfirming = tx.status.type === 'confirming';
                const isFinalized = tx.status.type === 'finalized';

                return (
                  <tr
                    key={tx.id}
                    onClick={() => onSelectTx(tx)}
                    className={`transition-colors cursor-pointer group ${
                      isReorg 
                        ? 'bg-rose-50/20 hover:bg-rose-50/40 text-slate-400' 
                        : 'hover:bg-slate-50/80 text-slate-800'
                    }`}
                  >
                    {/* Direction */}
                    <td className="py-3 px-4 font-bold whitespace-nowrap">
                      {isReorg ? (
                        <del className="text-slate-400">{tx.direction}</del>
                      ) : tx.direction === 'BUY' ? (
                        <span className="text-emerald-600">{tx.direction}</span>
                      ) : (
                        <span className="text-rose-500">{tx.direction}</span>
                      )}
                    </td>

                    {/* Pair */}
                    <td className="py-3 px-4 whitespace-nowrap">
                      <span className={`inline-flex items-center gap-1 font-semibold ${isReorg ? 'line-through text-slate-400' : 'text-slate-800'}`}>
                        <TokenPair symbols={tx.pair.hops ?? [tx.pair.fromToken, tx.pair.toToken]} />
                        {tx.pair.isAggregator && <span className="bg-[#0f1d33] text-white text-[10px] font-bold px-1.5 py-0.5 rounded">{tx.pair.aggregatorLabel || '聚合器'}</span>}
                      </span>
                    </td>

                    {/* DEX */}
                    <td className="py-3 px-4 whitespace-nowrap">
                      {isReorg ? (
                        <span className="text-slate-400">{tx.dex}</span>
                      ) : tx.dex === '1inch' ? (
                        <span className="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[11px] font-medium border border-slate-200">
                          1inch
                        </span>
                      ) : (
                        <span className="text-slate-600 font-medium">{tx.dex}</span>
                      )}
                    </td>

                    {/* Amount */}
                    <td className="py-3 px-4 text-right whitespace-nowrap font-mono">
                      <div className={`font-semibold ${isReorg ? 'line-through text-slate-400' : 'text-emerald-600'}`}>
                        <span className="font-sans text-[10px] mr-1">收到</span>+{formatAmount(tx.rawAmount, tx.pair.toToken, true)} {tx.pair.toToken}
                      </div>
                      <div className={`mt-1 text-[10px] text-slate-500 ${isReorg ? 'line-through' : ''}`}>
                        <span className="font-sans mr-1">付出</span>-{formatAmount(tx.paidAmount, tx.pair.fromToken, true)} {tx.pair.fromToken}
                      </div>
                    </td>

                    {/* Status */}
                    <td className="py-3 px-4 pl-6 whitespace-nowrap">
                      {tx.status.type === 'reorg' ? (
                        <span className="text-rose-600 font-semibold text-xs flex items-center gap-1">
                          已回滚 reorg
                        </span>
                      ) : tx.status.type === 'confirming' && tx.status.current < tx.status.total ? (
                        <div className="w-28 space-y-1">
                          <div className="text-blue-600 font-mono text-[11px] font-bold">
                            {tx.status.current}/{tx.status.total} 确认
                          </div>
                          <div className="w-full bg-slate-100 rounded-full h-1 overflow-hidden">
                            <div
                              className="bg-blue-600 h-1 rounded-full transition-all duration-300"
                              style={{
                                width: `${Math.min(100, (tx.status.current / tx.status.total) * 100)}%`,
                              }}
                            />
                          </div>
                        </div>
                      ) : (
                        <span className="text-emerald-600 font-semibold text-[11px]">
                          已确认
                        </span>
                      )}
                    </td>

                    {/* TX HASH */}
                    <td className="py-3 px-4 font-mono text-slate-400 whitespace-nowrap">
                      {isReorg ? (
                        <span className="text-slate-300">{formatHash(tx.txHash)}</span>
                      ) : (
                        <span className="text-slate-500 hover:text-blue-600 hover:underline">
                          {formatHash(tx.txHash)}
                        </span>
                      )}
                    </td>

                    {/* Time */}
                    <td className="py-3 px-4 text-right text-slate-400 whitespace-nowrap">
                      {formatRelativeTime(tx.timestamp)}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Table Footer */}
      <div className="p-3 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400 shrink-0 select-none">
        <div className="flex items-center gap-3">
          <span>显示 {filteredSwaps.length} 条</span>
          <button
            onClick={onTriggerReorgSimulation}
            className="text-[10px] text-slate-400 hover:text-rose-600 hover:underline transition-colors cursor-pointer hidden md:inline"
            title="模拟一次区块链深重组 (Reorg) 事务回滚"
          >
            [测试 Reorg 告警]
          </button>
        </div>

        <div className="flex items-center gap-4 font-mono">
          <span>
            最新区块：<strong className="text-slate-600 font-bold">{latestBlock.toLocaleString()}</strong>
          </span>
          <span>检索：42ms</span>
        </div>
      </div>
    </div>
  );
};
