import { useEffect, useRef, useState } from 'react';
import { ApiError, createQuote } from '../api/anchor';
import { isMockMode } from '../config/env';
import { Token } from '../types';
import { feeTierToPercent, fromRawAmount, slippagePercentToBps, toRawAmount } from '../utils/amount';
import {
  createMockHash,
  DEFAULT_SLIPPAGE,
  getMinimumReceived,
  getRouteQuotes,
  MAX_SLIPPAGE,
  MOCK_INITIAL_NONCE,
  QUOTE_DELAY_MS,
  TRANSACTION_STEP_MS,
} from '../utils/swap';

export type DemoState = 'empty' | 'quoting' | 'insufficient' | 'approval' | 'ready' | 'progress' | 'failed';
type ExecuteSwap = (from: string, to: string, paid: number, received: number, hash: string) => void;

export type RouteQuote = {
  fee: number;
  priceImpact: number;
  amount: number;
  amountOutMinimum?: number;
  gasEstimate?: string;
  l1DataFeeWei?: string;
  quoteId?: string;
};

export const DEMO_STATES: { value: DemoState; label: string }[] = [
  { value: 'empty', label: '未输入' }, { value: 'quoting', label: '报价中' },
  { value: 'insufficient', label: '余额不足' }, { value: 'approval', label: '需授权' },
  { value: 'ready', label: '可兑换' }, { value: 'progress', label: '交易进行中' },
  { value: 'failed', label: '交易失败' },
];

const FEE_DISPLAY = [0.01, 0.05, 0.3, 1] as const;
const QUOTE_DEBOUNCE_MS = 400;

export function useSwapDemo(
  tokens: Record<string, Token>,
  onExecuteSwap: ExecuteSwap,
  options?: { chainId?: number; live?: boolean },
) {
  const live = options?.live ?? !isMockMode();
  const chainId = options?.chainId ?? 8453;

  const [fromSymbol, setFromSymbol] = useState('WETH');
  const [toSymbol, setToSymbol] = useState('USDC');
  const [fromAmount, setFromAmount] = useState('1.0');
  const [slippage, setSlippage] = useState('0.5');
  const [customSlippage, setCustomSlippage] = useState('');
  const [isQuoting, setIsQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [liveRoutes, setLiveRoutes] = useState<RouteQuote[]>([]);
  const [forcedState, setForcedState] = useState<DemoState | null>(null);
  const [authorized, setAuthorized] = useState<string[]>(['WETH', 'ETH']);
  const [isApproving, setIsApproving] = useState(false);
  const [step, setStep] = useState(0);
  const [transaction, setTransaction] = useState<{ from: string; to: string; paid: number; received: number; hash: string; nonce: number; commit: boolean } | null>(null);
  const executeRef = useRef(onExecuteSwap);
  const committed = useRef(false);
  const nextNonce = useRef(MOCK_INITIAL_NONCE);
  const quoteSeq = useRef(0);
  useEffect(() => { executeRef.current = onExecuteSwap; }, [onExecuteSwap]);

  const fromToken = tokens[fromSymbol];
  const toToken = tokens[toSymbol];
  const numFrom = Number(fromAmount);
  const validAmount = Number.isFinite(numFrom) && numFrom > 0 && Boolean(fromToken) && Boolean(toToken);
  const effectiveSlippage = Number(customSlippage || slippage);
  const validSlippage = Number.isFinite(effectiveSlippage) && effectiveSlippage >= 0 && effectiveSlippage <= MAX_SLIPPAGE;

  const mockRoutes: RouteQuote[] = getRouteQuotes(numFrom, fromToken, toToken);
  const routes = live ? liveRoutes : mockRoutes;
  const bestRoute = routes.reduce<RouteQuote | undefined>((best, route) => (!best || route.amount > best.amount ? route : best), undefined);
  const received = bestRoute?.amount ?? 0;
  const minimumReceived = live
    ? (bestRoute?.amountOutMinimum ?? 0)
    : getMinimumReceived(received, validSlippage ? effectiveSlippage : DEFAULT_SLIPPAGE, toToken?.decimals ?? 18);
  const isBusy = transaction !== null && step < 4;
  const insufficient = validAmount && numFrom > (fromToken?.balance ?? 0);
  const needsApproval = fromSymbol !== 'ETH' && !authorized.includes(fromSymbol);
  const state: DemoState = isBusy
    ? 'progress'
    : forcedState ?? (!validAmount ? 'empty' : isQuoting ? 'quoting' : insufficient ? 'insufficient' : needsApproval ? 'approval' : received > 0 || !live ? 'ready' : quoteError ? 'failed' : 'empty');

  // mock 报价延迟
  useEffect(() => {
    if (live) {
      return;
    }
    if (!validAmount) {
      setIsQuoting(false);
      return;
    }
    setIsQuoting(true);
    const timer = setTimeout(() => setIsQuoting(false), QUOTE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [fromAmount, fromSymbol, toSymbol, validAmount, live]);

  // 真实报价
  useEffect(() => {
    if (!live) {
      return;
    }
    if (!validAmount || !fromToken || !toToken || fromToken.address === toToken.address || !validSlippage) {
      setLiveRoutes([]);
      setIsQuoting(false);
      setQuoteError(null);
      return;
    }
    const seq = ++quoteSeq.current;
    setIsQuoting(true);
    setQuoteError(null);
    const timer = window.setTimeout(() => {
      const amountIn = toRawAmount(fromAmount, fromToken.decimals);
      createQuote({
        chainId,
        tokenIn: fromToken.symbol === 'ETH' ? '0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee' : fromToken.address,
        tokenOut: toToken.symbol === 'ETH' ? '0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee' : toToken.address,
        amountIn,
        slippageBps: slippagePercentToBps(effectiveSlippage),
      })
        .then((quote) => {
          if (seq !== quoteSeq.current) {
            return;
          }
          const amount = fromRawAmount(quote.amountOut, quote.tokenOut.decimals ?? toToken.decimals);
          const minOut = fromRawAmount(quote.amountOutMinimum, quote.tokenOut.decimals ?? toToken.decimals);
          const bestFee = feeTierToPercent(quote.fee);
          setLiveRoutes(
            FEE_DISPLAY.map((fee) =>
              Math.abs(fee - bestFee) < 1e-9
                ? {
                    fee,
                    priceImpact: quote.priceImpactBps / 10_000,
                    amount,
                    amountOutMinimum: minOut,
                    gasEstimate: quote.gasEstimate,
                    l1DataFeeWei: quote.l1DataFeeWei,
                    quoteId: quote.quoteId,
                  }
                : { fee, priceImpact: 0, amount: 0 },
            ),
          );
          setIsQuoting(false);
        })
        .catch((err: unknown) => {
          if (seq !== quoteSeq.current) {
            return;
          }
          setLiveRoutes([]);
          setIsQuoting(false);
          setQuoteError(err instanceof ApiError ? err.message : '报价失败');
        });
    }, QUOTE_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [live, validAmount, fromAmount, fromToken, toToken, validSlippage, effectiveSlippage, chainId]);

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

  const reset = () => { setForcedState(null); setTransaction(null); setIsApproving(false); setQuoteError(null); };
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
    if (live) {
      // P5 前：真实模式只展示报价，不模拟成交改余额
      setQuoteError('真实兑换将在 P5 接入；当前仅支持报价');
      return;
    }
    committed.current = false;
    setStep(0);
    setTransaction({ from: fromSymbol, to: toSymbol, paid: numFrom, received, hash: createMockHash(), nonce: nextNonce.current++, commit });
    setForcedState(null);
  };
  const selectDemoState = (next: DemoState) => {
    if (live) {
      return;
    }
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

  return {
    fromSymbol, toSymbol, fromAmount, fromToken, toToken, numFrom,
    slippage, setSlippage, customSlippage, setCustomSlippage, effectiveSlippage, validSlippage,
    routes, bestRoute, received, minimumReceived, state, isBusy, isApproving, step, transaction,
    quoteError, live, updateAmount, selectToken, invert, selectDemoState, act,
  };
}
