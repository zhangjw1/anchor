export function FooterBar() {
  return <footer className="h-9 bg-white border-t border-slate-200 px-4 flex items-center justify-between gap-4 text-xs text-slate-500 shrink-0 select-none">
    <div className="truncate text-[11px]">演示数据为 mock；钱包余额、路由报价与交易状态均为本地模拟。</div>
    <div className="font-mono text-[11px] whitespace-nowrap text-slate-600">RPC QPS: 33 · 错误 0.2%</div>
  </footer>;
}
