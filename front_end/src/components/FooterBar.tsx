import type { ChainMetrics } from '../hooks/useChainMetrics';

interface FooterBarProps {
  metrics: ChainMetrics;
  mock: boolean;
}

function pct(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`;
}

export function FooterBar({ metrics, mock }: FooterBarProps) {
  if (mock) {
    return (
      <footer className="h-9 bg-white border-t border-slate-200 px-4 flex items-center justify-between gap-4 text-xs text-slate-500 shrink-0 select-none">
        <div className="truncate text-[11px]">Mock 模式：状态栏使用本地占位数据。</div>
      </footer>
    );
  }

  const status = metrics.connected ? '已连接' : '重连中';
  const kafka = metrics.kafkaLag == null ? '—' : String(metrics.kafkaLag);

  return (
    <footer className="h-9 bg-white border-t border-slate-200 px-4 flex items-center gap-4 text-[11px] text-slate-500 shrink-0 select-none overflow-x-auto font-mono">
      <span className={metrics.connected ? 'text-emerald-600' : 'text-amber-600'}>{status}</span>
      <span>头块 {metrics.headBlock || '—'}</span>
      <span>已扫 {metrics.processedBlock || '—'}</span>
      <span>落后 {metrics.lagBlocks}</span>
      <span>RPC {metrics.rpcQps.toFixed(2)}/s</span>
      <span>错误率 {pct(metrics.rpcErrorRate)}</span>
      <span>Kafka lag {kafka}</span>
      <span>Reorg {metrics.reorgCount}/{metrics.reorgDepth}</span>
    </footer>
  );
}
