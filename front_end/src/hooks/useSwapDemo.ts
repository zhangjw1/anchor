import { useEffect, useRef, useState } from 'react';
import { Token } from '../types';
import { createMockHash, DEFAULT_SLIPPAGE, getMinimumReceived, getRouteQuotes, MAX_SLIPPAGE, MOCK_INITIAL_NONCE, QUOTE_DELAY_MS, TRANSACTION_STEP_MS } from '../utils/swap';

export type DemoState = 'empty' | 'quoting' | 'insufficient' | 'approval' | 'ready' | 'progress' | 'failed';
type ExecuteSwap = (from: string, to: string, paid: number, received: number, hash: string) => void;

export const DEMO_STATES: { value: DemoState; label: string }[] = [
  { value: 'empty', label: '未输入' }, { value: 'quoting', label: '报价中' },
  { value: 'insufficient', label: '余额不足' }, { value: 'approval', label: '需授权' },
  { value: 'ready', label: '可兑换' }, { value: 'progress', label: '交易进行中' },
  { value: 'failed', label: '交易失败' },
];

export function useSwapDemo(tokens: Record<string, Token>, onExecuteSwap: ExecuteSwap) {
  const [fromSymbol, setFromSymbol] = useState('WETH');
  const [toSymbol, setToSymbol] = useState('USDC');
  const [fromAmount, setFromAmount] = useState('1.0');
  const [slippage, setSlippage] = useState('0.5');
  const [customSlippage, setCustomSlippage] = useState('');
  const [isQuoting, setIsQuoting] = useState(false);
  const [forcedState, setForcedState] = useState<DemoState | null>(null);
  const [authorized, setAuthorized] = useState<string[]>(['WETH', 'ETH']);
  const [isApproving, setIsApproving] = useState(false);
  const [step, setStep] = useState(0);
  const [transaction, setTransaction] = useState<{ from: string; to: string; paid: number; received: number; hash: string; nonce: number; commit: boolean } | null>(null);
  const executeRef = useRef(onExecuteSwap);
  const committed = useRef(false);
  const nextNonce = useRef(MOCK_INITIAL_NONCE);
  useEffect(() => { executeRef.current = onExecuteSwap; }, [onExecuteSwap]);

  const fromToken = tokens[fromSymbol];
  const toToken = tokens[toSymbol];
  const numFrom = Number(fromAmount);
  const validAmount = Number.isFinite(numFrom) && numFrom > 0;
  const effectiveSlippage = Number(customSlippage || slippage);
  const validSlippage = Number.isFinite(effectiveSlippage) && effectiveSlippage >= 0 && effectiveSlippage <= MAX_SLIPPAGE;
  const routes = getRouteQuotes(numFrom, fromToken, toToken);
  const bestRoute = routes.reduce<(typeof routes)[number] | undefined>((best, route) => !best || route.amount > best.amount ? route : best, undefined);
  const received = bestRoute?.amount ?? 0;
  const minimumReceived = getMinimumReceived(received, validSlippage ? effectiveSlippage : DEFAULT_SLIPPAGE, toToken.decimals);
  const isBusy = transaction !== null && step < 4;
  const insufficient = validAmount && numFrom > fromToken.balance;
  const needsApproval = fromSymbol !== 'ETH' && !authorized.includes(fromSymbol);
  const state: DemoState = isBusy ? 'progress' : forcedState ?? (!validAmount ? 'empty' : isQuoting ? 'quoting' : insufficient ? 'insufficient' : needsApproval ? 'approval' : 'ready');

  useEffect(() => {
    if (!validAmount) { setIsQuoting(false); return; }
    setIsQuoting(true);
    const timer = setTimeout(() => setIsQuoting(false), QUOTE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [fromAmount, fromSymbol, toSymbol, validAmount]);

  useEffect(() => {
    if (!isApproving) return;
    const timer = setTimeout(() => {
      setAuthorized((current) => [...new Set([...current, fromSymbol])]);
      setIsApproving(false);
    }, TRANSACTION_STEP_MS);
    return () => clearTimeout(timer);
  }, [isApproving, fromSymbol]);

  useEffect(() => {
    if (!transaction || step >= 4) return;
    const timer = setTimeout(() => {
      if (step === 3 && transaction.commit && !committed.current) {
        committed.current = true;
        executeRef.current(transaction.from, transaction.to, transaction.paid, transaction.received, transaction.hash);
      }
      setStep(step + 1);
    }, TRANSACTION_STEP_MS);
    return () => clearTimeout(timer);
  }, [transaction, step]);

  const reset = () => { setForcedState(null); setTransaction(null); setIsApproving(false); };
  const updateAmount = (value: string) => { reset(); setFromAmount(value); };
  const selectToken = (side: 'from' | 'to', symbol: string) => {
    if (!tokens[symbol] || isBusy) return;
    reset();
    if (side === 'from') { setFromSymbol(symbol); if (toSymbol === symbol) setToSymbol(fromSymbol); }
    else { setToSymbol(symbol); if (fromSymbol === symbol) setFromSymbol(toSymbol); }
  };
  const invert = () => { if (isBusy) return; reset(); setFromSymbol(toSymbol); setToSymbol(fromSymbol); };
  const startTransaction = (commit: boolean) => {
    if (!validAmount || insufficient || !bestRoute || !validSlippage || isBusy || isApproving) return;
    committed.current = false;
    setStep(0);
    setTransaction({ from: fromSymbol, to: toSymbol, paid: numFrom, received, hash: createMockHash(), nonce: nextNonce.current++, commit });
    setForcedState(null);
  };
  const selectDemoState = (next: DemoState) => {
    reset();
    setFromSymbol('WETH'); setToSymbol('USDC');
    setSlippage('0.5'); setCustomSlippage('');
    setFromAmount(next === 'empty' ? '' : next === 'insufficient' ? String(tokens.WETH.balance + 1) : String(Math.min(1, tokens.WETH.balance)));
    setIsQuoting(false);
    setAuthorized(next === 'approval' ? [] : ['WETH', 'ETH']);
    setForcedState(next === 'quoting' || next === 'failed' ? next : null);
    if (next === 'progress') {
      const amount = Math.min(1, tokens.WETH.balance);
      const quotes = getRouteQuotes(amount, tokens.WETH, tokens.USDC);
      const output = Math.max(0, ...quotes.map((route) => route.amount));
      setStep(0);
      setTransaction({ from: 'WETH', to: 'USDC', paid: amount, received: output, hash: createMockHash(), nonce: nextNonce.current, commit: false });
    }
  };
  const act = () => {
    if (isBusy || isApproving || !validAmount || insufficient || !validSlippage || state === 'quoting') return;
    if (needsApproval) setIsApproving(true);
    else startTransaction(true);
  };

  return { fromSymbol, toSymbol, fromAmount, fromToken, toToken, numFrom, slippage, setSlippage, customSlippage, setCustomSlippage, effectiveSlippage, validSlippage, routes, bestRoute, received, minimumReceived, state, isBusy, isApproving, step, transaction, updateAmount, selectToken, invert, selectDemoState, act };
}
