import React, { useState } from 'react';
import { ExternalLink, Copy, Check, AlertOctagon, CheckCircle2, Clock, ShieldAlert } from 'lucide-react';
import { SwapTransaction } from '../types';
import { TokenPair } from './TokenPair';

interface TxDetailModalProps {
  tx: SwapTransaction | null;
  onClose: () => void;
  explorerUrl: string;
}

export const TxDetailModal: React.FC<TxDetailModalProps> = ({
  tx,
  onClose,
  explorerUrl,
}) => {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  if (!tx) return null;

  const copyText = (val: string, key: string) => {
    navigator.clipboard.writeText(val);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 1500);
  };

  const isReorg = tx.status.type === 'reorg';
  const isConfirming = tx.status.type === 'confirming';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
      <div className="bg-white rounded-xl shadow-2xl border border-slate-200 max-w-lg w-full overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/70">
          <div className="flex items-center gap-2">
            <span className="font-bold text-slate-900 text-sm">交易深度详情 (Tx Inspector)</span>
            {isReorg && (
              <span className="bg-rose-100 text-rose-700 text-[10px] font-bold px-2 py-0.5 rounded">
                REORG 已回滚
              </span>
            )}
            {tx.watchedAddressMatch && (
              <span className="bg-[#0f1d33] text-white text-[10px] font-bold px-2 py-0.5 rounded">
                监控命中: {tx.watchedAddressMatch}
              </span>
            )}
          </div>
          <button
            onClick={onClose}
            className="w-7 h-7 rounded-md flex items-center justify-center text-slate-400 hover:text-slate-700 hover:bg-slate-200/50 cursor-pointer text-sm font-semibold"
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="p-5 overflow-y-auto space-y-4 text-xs">
          {/* Status Alert Banner */}
          {tx.status.type === 'reorg' ? (
            <div className="bg-rose-50 border border-rose-200 rounded-lg p-3 text-rose-800 flex items-start gap-2.5">
              <AlertOctagon className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <div>
                <div className="font-bold">该交易所在区块已被区块链分叉回滚 (Chain Reorg)</div>
                <div className="text-[11px] text-rose-600 mt-0.5">
                  交易已被系统判定失效，状态回滚至前置区块，已从当前有效状态树移除。
                </div>
              </div>
            </div>
          ) : tx.status.type === 'confirming' ? (
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 text-blue-900 flex items-start gap-2.5">
              <Clock className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
              <div className="w-full">
                <div className="font-bold flex items-center justify-between">
                  <span>区块打包中 · 当前确认数 {tx.status.current} / {tx.status.total}</span>
                  <span className="font-mono text-xs">{Math.round((tx.status.current / tx.status.total) * 100)}%</span>
                </div>
                <div className="w-full bg-blue-100 rounded-full h-1.5 mt-1.5 overflow-hidden">
                  <div 
                    className="bg-blue-600 h-1.5 rounded-full" 
                    style={{ width: `${(tx.status.current / tx.status.total) * 100}%` }} 
                  />
                </div>
              </div>
            </div>
          ) : (
            <div className="bg-emerald-50 border border-emerald-200 rounded-lg p-3 text-emerald-900 flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span className="font-bold">交易已最终确认 (Finalized Block)</span>
            </div>
          )}

          {/* Key Properties Grid */}
          <div className="divide-y divide-slate-100 border border-slate-200 rounded-lg overflow-hidden font-mono">
            {/* Hash */}
            <div className="p-2.5 flex flex-wrap gap-2 items-center justify-between hover:bg-slate-50">
              <span className="text-slate-400 font-sans">Tx Hash</span>
              <div className="flex items-center gap-1.5 text-slate-800">
                <span className="break-all min-w-0">{tx.txHash}</span>
                <button
                  onClick={() => copyText(tx.txHash, 'hash')}
                  className="p-1 hover:text-blue-600 cursor-pointer"
                  title="复制"
                >
                  {copiedKey === 'hash' ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                </button>
                <a
                  href={`${explorerUrl}/tx/${tx.txHash}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="p-1 hover:text-blue-600"
                  title="前往 Basescan"
                >
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            </div>

            {/* Block Number */}
            <div className="p-2.5 flex items-center justify-between hover:bg-slate-50">
              <span className="text-slate-400 font-sans">区块高度</span>
              <span className="font-bold text-slate-800">#{tx.blockNumber.toLocaleString()}</span>
            </div>

            {/* Direction & DEX */}
            <div className="p-2.5 flex items-center justify-between hover:bg-slate-50">
              <span className="text-slate-400 font-sans">交易类型 / 协议</span>
              <span className="text-slate-800 font-sans">
                <strong className={tx.direction === 'BUY' ? 'text-emerald-600' : 'text-rose-500'}>
                  {tx.direction}
                </strong>
                {' · '}
                <span className="font-semibold">{tx.dex}</span>
              </span>
            </div>

            {/* Routing Path */}
            <div className="p-2.5 flex items-center justify-between hover:bg-slate-50">
              <span className="text-slate-400 font-sans">兑换路由</span>
              <span className="text-slate-800 font-sans font-semibold">
                <TokenPair symbols={tx.pair.hops ?? [tx.pair.fromToken, tx.pair.toToken]} />
              </span>
            </div>

            {/* Amount */}
            <div className="p-2.5 flex items-center justify-between hover:bg-slate-50">
              <span className="text-slate-400 font-sans">成交数量</span>
              <span className="font-bold text-slate-900">{tx.amount}</span>
            </div>

            {/* Gas Fee */}
            <div className="p-2.5 flex items-center justify-between hover:bg-slate-50">
              <span className="text-slate-400 font-sans">Gas 消耗</span>
              <span className="text-slate-700">
                {tx.gasUsed.toLocaleString()} units (~${tx.gasFeeUsd.toFixed(3)})
              </span>
            </div>

            {/* Sender Address */}
            <div className="p-2.5 flex items-center justify-between hover:bg-slate-50">
              <span className="text-slate-400 font-sans">发起地址 (From)</span>
              <div className="flex items-center gap-1.5 text-slate-800">
                <span>{tx.senderAddress.slice(0, 8)}...{tx.senderAddress.slice(-6)}</span>
                <button
                  onClick={() => copyText(tx.senderAddress, 'from')}
                  className="p-1 hover:text-blue-600 cursor-pointer"
                >
                  {copiedKey === 'from' ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                </button>
              </div>
            </div>
          </div>

          {/* Decoded Event Logs Preview */}
          <div>
            <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
              解码事件日志 (Decoded Logs)
            </div>
            <div className="bg-slate-900 text-slate-300 p-3 rounded-lg font-mono text-[11px] leading-relaxed overflow-x-auto">
              <div className="text-cyan-400 font-bold">// event Swap(sender, recipient, amount0, amount1, sqrtPriceX96, liquidity, tick)</div>
              <div>topic[0]: 0xc42079f94a6350d7e6235f29174924f928cc983e828683ddd0d3d45c53fb375f</div>
              <div>sender: {tx.senderAddress}</div>
              <div>recipient: {tx.recipientAddress}</div>
              <div>dex: {tx.dex} (Pool v3)</div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-3 bg-slate-50 border-t border-slate-100 flex items-center justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white font-medium rounded-md text-xs cursor-pointer"
          >
            关闭详情
          </button>
        </div>
      </div>
    </div>
  );
};
