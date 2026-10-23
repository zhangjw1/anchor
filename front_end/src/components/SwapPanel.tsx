import { useState } from 'react';
import { Settings, ArrowDown, Loader2 } from 'lucide-react';
import { Token } from '../types';
import { useSwapDemo } from '../hooks/useSwapDemo';
import { formatAmount, MAX_SLIPPAGE } from '../utils/swap';
import { TokenSelector } from './TokenSelector';
import { DemoStatePanel } from './DemoStatePanel';
import { TransactionSteps } from './TransactionSteps';

interface SwapPanelProps {
  tokens: Record<string, Token>;
  onExecuteSwap: (from: string, to: string, paid: number, received: number, hash: string) => void;
}

export function SwapPanel({ tokens, onExecuteSwap }: SwapPanelProps) {
  const swap = useSwapDemo(tokens, onExecuteSwap);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const { fromSymbol, toSymbol, fromToken, fromAmount, state, isBusy, isApproving } = swap;
  const locked = isBusy || isApproving;
  const quoting = state === 'quoting';
  const disabled = ['empty', 'quoting', 'insufficient'].includes(state) || locked || !swap.validSlippage;
  const buttonText = isApproving ? `授权 ${fromSymbol} 中...` : isBusy ? '交易进行中...' : swap.transaction && swap.step === 4 && state === 'ready' ? '已确认 · 再次兑换' : !swap.validSlippage ? '请检查滑点' : ({ empty: '请输入卖出数量', quoting: '报价中...', insufficient: '余额不足', approval: `授权 ${fromSymbol}`, ready: '兑换 (Swap)', progress: '兑换 (Swap)', failed: '重试兑换' })[state];
  const hasQuote = swap.received > 0 && state !== 'empty' && !quoting;

  return <div className="bg-white p-4 flex flex-col flex-1 relative" data-testid="swap-panel">
    <div className="flex items-center justify-between mb-3">
      <span className="text-xs font-bold tracking-wider text-slate-800 uppercase">SWAP 兑换</span>
      <button disabled={locked} onClick={() => setSettingsOpen(!settingsOpen)} aria-expanded={settingsOpen} aria-label="兑换设置" className="text-slate-400 hover:text-slate-700 p-1 rounded hover:bg-slate-50"><Settings className="w-4 h-4" /></button>
    </div>

    {settingsOpen && <div className="mb-3 p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-2">
      <div className="flex items-center justify-between text-slate-600 font-medium"><span>滑点容差 (Slippage)</span><span className="text-blue-600">{swap.effectiveSlippage}%</span></div>
      <div className="grid grid-cols-4 gap-1.5">
        {['0.1', '0.5', '1.0'].map((value) => <button key={value} disabled={locked} onClick={() => { swap.setSlippage(value); swap.setCustomSlippage(''); }} className={`py-1 rounded text-center font-medium border ${swap.slippage === value && !swap.customSlippage ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-100'}`}>{value}%</button>)}
        <input type="number" aria-label="自定义滑点" min="0" max={MAX_SLIPPAGE} step="0.1" disabled={locked} placeholder="自定义" value={swap.customSlippage} onChange={(event) => swap.setCustomSlippage(event.target.value)} className="min-w-0 bg-white border border-slate-200 rounded px-1 py-1 text-center font-mono text-xs" />
      </div>
      {!swap.validSlippage && <p role="alert" className="text-rose-600">滑点需为 0–{MAX_SLIPPAGE}% 之间的有效数字。</p>}
    </div>}

    <div className="bg-[#f8fafc] border border-slate-200 rounded-lg p-3 space-y-1">
      <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
        <span>卖出 (SELL)</span><span className="font-mono" data-testid="sell-balance">余额: {formatAmount(fromToken.balance, fromSymbol)} {fromSymbol}</span>
      </div>
      <div className="flex items-center justify-between gap-2 pt-1">
        <input aria-label="卖出数量" type="number" min="0" step="any" disabled={locked} value={fromAmount} onChange={(event) => swap.updateAmount(event.target.value)} placeholder="0.0" className="min-w-0 w-full bg-transparent font-mono text-xl font-bold text-slate-900 focus:outline-none" />
        <button disabled={locked} onClick={() => swap.updateAmount(String(fromToken.balance))} className="text-[10px] font-bold text-blue-600 hover:text-blue-800 disabled:text-slate-400" aria-label="使用最大余额">MAX</button>
        <TokenSelector symbol={fromSymbol} symbols={Object.keys(tokens)} label="卖出代币" disabled={locked} onSelect={(symbol) => swap.selectToken('from', symbol)} />
      </div>
    </div>

    <div className="flex justify-center -my-2.5 z-10"><button disabled={locked} onClick={swap.invert} className="w-7 h-7 rounded-full bg-white border border-slate-200 shadow-xs flex items-center justify-center text-slate-500 hover:text-slate-900" aria-label="反转交易方向"><ArrowDown className="w-3.5 h-3.5" /></button></div>

    <div className="bg-[#f8fafc] border border-slate-200 rounded-lg p-3 space-y-1">
      <div className="text-xs text-slate-500 font-medium">买入 (BUY)</div>
      <div className="flex items-center justify-between gap-2 pt-1">
        <input aria-label="买入数量" type="text" readOnly value={quoting ? '报价中...' : formatAmount(hasQuote ? swap.received : 0, toSymbol)} className="min-w-0 w-full bg-transparent font-mono text-xl font-bold text-slate-900 focus:outline-none" />
        <TokenSelector symbol={toSymbol} symbols={Object.keys(tokens)} label="买入代币" disabled={locked} onSelect={(symbol) => swap.selectToken('to', symbol)} />
      </div>
    </div>

    <button data-testid="swap-action" disabled={disabled} onClick={swap.act} className="w-full mt-3 bg-[#0c1a30] hover:bg-[#152a4a] disabled:bg-slate-200 disabled:text-slate-500 text-white font-bold py-3 px-4 rounded-md text-sm transition-colors shadow-xs disabled:cursor-not-allowed flex items-center justify-center gap-2">
      {(quoting || locked) && <Loader2 className="w-4 h-4 animate-spin" />}<span>{buttonText}</span>
    </button>
    {state === 'failed' && <div role="alert" className="mt-2 text-xs text-rose-700 bg-rose-50 border border-rose-200 p-2 rounded"><strong>交易失败</strong><p className="font-mono mt-1">revert: Too little received</p><p className="mt-1">收到数量低于最少收到，请调整滑点后重试。</p></div>}
    {swap.transaction && <TransactionSteps step={swap.step} hash={swap.transaction.hash} nonce={swap.transaction.nonce} />}

    <div className="mt-3 pt-3 border-t border-slate-100 text-xs space-y-1.5">
      <div className="flex items-center justify-between"><span className="text-slate-500">价格影响</span><span className="text-emerald-600 font-mono font-medium">{hasQuote ? `${((swap.bestRoute?.priceImpact ?? 0) * 100).toFixed(2)}%` : '—'}</span></div>
      <div className="flex items-center justify-between"><span className="text-slate-500">预估 Gas</span><span className="font-mono text-slate-700">{hasQuote ? '~$0.02' : '—'}</span></div>
      <button disabled={locked} onClick={() => setSettingsOpen(!settingsOpen)} className="w-full flex items-center justify-between text-slate-500 hover:text-blue-600" aria-expanded={settingsOpen} aria-label="修改滑点"><span>滑点 {swap.validSlippage ? swap.effectiveSlippage : '—'}%</span><span className="text-blue-600">点击修改</span></button>
      <div className="flex items-center justify-between gap-2"><span className="text-slate-500 shrink-0">最少收到</span><span data-testid="minimum-received" className="font-mono text-slate-700 text-right">{hasQuote && swap.validSlippage ? `${formatAmount(swap.minimumReceived, toSymbol)} ${toSymbol}` : '—'}</span></div>
      <div className="pt-1"><div className="text-slate-500 mb-1">路由报价 <span className="text-[10px]">· mock</span></div>
        <div className="space-y-1" data-testid="route-quotes">
          {[0.01, 0.05, 0.3, 1].map((fee) => {
            const route = swap.routes.find((item) => item.fee === fee);
            const best = hasQuote && swap.bestRoute?.fee === fee;
            return <div key={fee} className={`flex items-center justify-between gap-1 px-2 py-1.5 rounded border text-[10px] ${best ? 'bg-blue-50 border-blue-200 text-blue-800' : 'bg-slate-50 border-slate-100 text-slate-600'}`}>
              <span className="shrink-0">Uniswap V3 ({fee}%) {best && <span className="text-[9px] font-bold text-blue-700">最优</span>}</span>
              <span className="font-mono text-right">{hasQuote && route ? `${formatAmount(route.amount, toSymbol)} ${toSymbol}` : '—'}</span>
            </div>;
          })}
        </div>
      </div>
    </div>
    <DemoStatePanel state={state} onChange={swap.selectDemoState} />
  </div>;
}
