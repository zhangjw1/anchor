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

  const kafkaBroken = metrics.connected && (metrics.kafkaLag == null || metrics.kafkaStale);
  const kafkaLabel = !metrics.connected
    ? '—'
    : kafkaBroken
      ? 'Kafka 异常'
      : `Kafka lag ${metrics.kafkaLag}`;

  return (
    <footer className="h-9 bg-white border-t border-slate-200 px-4 flex items-center gap-4 text-[11px] text-slate-500 shrink-0 select-none overflow-x-auto font-mono">
      <span className={metrics.connected ? 'text-emerald-600' : 'text-amber-600'}>
        {metrics.connected ? '已连接' : '重连中'}
      </span>
      <span>头块 {metrics.headBlock || '—'}</span>
      <span>已扫 {metrics.processedBlock || '—'}</span>
      <span>落后 {metrics.lagBlocks}</span>
      <span>RPC {metrics.rpcQps.toFixed(2)}/s</span>
      <span>错误率 {pct(metrics.rpcErrorRate)}</span>
      <span className={kafkaBroken ? 'text-amber-600 font-semibold' : undefined}>{kafkaLabel}</span>
      {metrics.outboxPending > 0 && (
        <span className="text-amber-600 font-semibold">待推送 {metrics.outboxPending}</span>
      )}
      <span>Reorg {metrics.reorgCount}/{metrics.reorgDepth}</span>
    </footer>
  );
}
