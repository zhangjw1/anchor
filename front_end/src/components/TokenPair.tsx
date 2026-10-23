import React from 'react';
import { ArrowRight } from 'lucide-react';

export function TokenPair({ symbols }: { symbols: string[] }) {
  return <span className="inline-flex items-center gap-1 whitespace-nowrap">
    {symbols.map((symbol, index) => <React.Fragment key={`${symbol}-${index}`}>
      {index > 0 && <ArrowRight aria-hidden="true" className="w-3 h-3 shrink-0 text-slate-400" />}
      <span>{symbol}</span>
    </React.Fragment>)}
  </span>;
}
