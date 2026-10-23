import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { DEMO_STATES, DemoState } from '../hooks/useSwapDemo';

export function DemoStatePanel({ state, onChange }: { state: DemoState; onChange: (state: DemoState) => void }) {
  const [open, setOpen] = useState(false);
  return <div className="mt-auto pt-3" data-testid="demo-panel">
    <button onClick={() => setOpen(!open)} aria-expanded={open} aria-controls="demo-states" className="w-full flex items-center justify-between border-t border-slate-200 pt-3 text-xs font-semibold text-slate-600 hover:text-slate-900">
      <span>演示状态</span><ChevronDown className={`w-3.5 h-3.5 transition-transform ${open ? 'rotate-180' : ''}`} />
    </button>
    {open && <div id="demo-states" className="mt-2 grid grid-cols-2 gap-1.5" role="group" aria-label="演示状态切换">
      {DEMO_STATES.map((item) => <button key={item.value} aria-pressed={state === item.value} onClick={() => onChange(item.value)} className={`text-left text-[11px] px-2 py-1.5 border rounded transition-colors ${state === item.value ? 'bg-blue-50 border-blue-300 text-blue-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50'}`}>{item.label}</button>)}
      <p className="col-span-2 text-[10px] text-slate-500">仅模拟页面状态，不发送链上交易。</p>
    </div>}
  </div>;
}
