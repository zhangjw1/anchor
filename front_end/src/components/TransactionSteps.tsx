import { Check, Loader2 } from 'lucide-react';

export function TransactionSteps({ step, hash, nonce }: { step: number; hash: string; nonce: number }) {
  const labels = ['模拟', `签名 (Nonce: ${nonce})`, '已广播 (tx hash)', 'Pending', '已确认'];
  return <ol className="my-3 text-xs" aria-label="交易进度" aria-live="polite">
    {labels.map((label, index) => <li key={label} aria-current={index === step ? 'step' : undefined} className={`relative flex gap-2 pb-3 last:pb-0 ${index <= step ? 'text-emerald-700' : 'text-slate-500'}`}>
      {index < labels.length - 1 && <span aria-hidden="true" className={`absolute left-[7px] top-4 bottom-0 border-l ${index < step ? 'border-emerald-300' : 'border-slate-200'}`} />}
      <span className={`relative w-4 h-4 rounded-full flex shrink-0 items-center justify-center ${index <= step ? 'bg-emerald-50' : 'bg-slate-100'}`}>
        {index < step || step === 4 ? <Check className="w-3 h-3" /> : index === step ? <Loader2 className="w-3 h-3 animate-spin" /> : <span className="w-1 h-1 rounded-full bg-slate-400" />}
      </span>
      <div className="min-w-0"><span className="font-medium">{label}</span>{index === 2 && step >= 2 && <div className="font-mono text-[10px] break-all text-slate-500 mt-1" data-testid="broadcast-hash">{hash}</div>}</div>
    </li>)}
  </ol>;
}
