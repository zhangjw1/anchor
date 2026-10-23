import { useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';

export function TokenSelector({ symbol, symbols, label, disabled, onSelect }: { symbol: string; symbols: string[]; label: string; disabled: boolean; onSelect: (symbol: string) => void }) {
  const [open, setOpen] = useState(false);
  return <div className="relative shrink-0" onKeyDown={(event) => { if (event.key === 'Escape') setOpen(false); }}>
    <button disabled={disabled} aria-label={label} aria-expanded={open} aria-haspopup="listbox" onClick={() => setOpen(!open)} className="flex items-center gap-1.5 bg-white border border-slate-200 hover:bg-slate-50 px-2.5 py-1.5 rounded-md font-semibold text-xs text-slate-800 shadow-xs disabled:cursor-not-allowed">
      <span>{symbol}</span><ChevronDown className="w-3.5 h-3.5 text-slate-400" />
    </button>
    {open && !disabled && <>
      <button tabIndex={-1} aria-label="关闭代币列表" className="fixed inset-0 z-40 cursor-default" onClick={() => setOpen(false)} />
      <div role="listbox" aria-label={label} className="absolute right-0 mt-1 w-32 bg-white border border-slate-200 rounded-md shadow-lg z-50 py-1">
        {symbols.map((item) => <button key={item} role="option" aria-selected={symbol === item} onClick={() => { onSelect(item); setOpen(false); }} className="w-full text-left px-3 py-1.5 hover:bg-slate-50 flex items-center justify-between text-xs text-slate-700">
          <span className="font-semibold">{item}</span>{symbol === item && <Check className="w-3 h-3 text-blue-600" />}
        </button>)}
      </div>
    </>}
  </div>;
}
