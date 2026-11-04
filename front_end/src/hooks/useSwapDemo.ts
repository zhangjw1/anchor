import { useEffect, useRef, useState } from 'react';
import { useAccount, useSendTransaction, useSwitchChain } from 'wagmi';
import { ApiError, createQuote, getTrade, prepareTrade, reportTrade, UnsignedTxDto } from '../api/anchor';
import { isMockMode, TRADE_ALLOWED_CHAIN_ID } from '../config/env';
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
const NATIVE = '0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee';

export function useSwapDemo(
  tokens: Record<string, Token>,
  onExecuteSwap: ExecuteSwap,
  options?: { chainId?: number; live?: boolean },
) {
  const live = options?.live ?? !isMockMode();
  const chainId = options?.chainId ?? 8453;
  const account = useAccount();
  const { sendTransactionAsync } = useSendTransaction();
  const { switchChainAsync } = useSwitchChain();

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
  const liveBusy = useRef(false);
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
  const isBusy = (transaction !== null && step < 4) || liveBusy.current;
  const insufficient = validAmount && numFrom > (fromToken?.balance ?? 0);
  const needsApproval = live ? false : (fromSymbol !== 'ETH' && !authorized.includes(fromSymbol));
  const state: DemoState = (transaction !== null && step < 4) || isApproving
    ? (isApproving && live ? 'approval' : 'progress')
    : forcedState ?? (!validAmount ? 'empty' : isQuoting ? 'quoting' : insufficient ? 'insufficient' : needsApproval ? 'approval' : received > 0 || !live ? 'ready' : quoteError ? 'failed' : 'empty');

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
        tokenIn: fromToken.symbol === 'ETH' ? NATIVE : fromToken.address,
        tokenOut: toToken.symbol === 'ETH' ? NATIVE : toToken.address,
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
    if (live || !isApproving) return;
    const timer = setTimeout(() => {
      setAuthorized((current) => [...new Set([...current, fromSymbol])]);
      setIsApproving(false);
    }, TRANSACTION_STEP_MS);
    return () => clearTimeout(timer);
  }, [isApproving, fromSymbol, live]);

  useEffect(() => {
    if (live || !transaction || step >= 4) return;
    const timer = setTimeout(() => {
      if (step === 3 && transaction.commit && !committed.current) {
        committed.current = true;
        executeRef.current(transaction.from, transaction.to, transaction.paid, transaction.received, transaction.hash);
      }
      setStep(step + 1);
    }, TRANSACTION_STEP_MS);
    return () => clearTimeout(timer);
  }, [transaction, step, live]);

  const reset = () => {
    setForcedState(null);
    setTransaction(null);
    setIsApproving(false);
    setQuoteError(null);
    liveBusy.current = false;
  };
  const updateAmount = (value: string) => { reset(); setFromAmount(value); };
  const selectToken = (side: 'from' | 'to', symbol: string) => {
    if (!tokens[symbol] || (transaction !== null && step < 4)) return;
    reset();
    if (side === 'from') { setFromSymbol(symbol); if (toSymbol === symbol) setToSymbol(fromSymbol); }
    else { setToSymbol(symbol); if (fromSymbol === symbol) setFromSymbol(toSymbol); }
  };
  const invert = () => {
    if (transaction !== null && step < 4) return;
    reset();
    setFromSymbol(toSymbol);
    setToSymbol(fromSymbol);
  };

  const sendUnsigned = async (tx: UnsignedTxDto) => {
    const hash = await sendTransactionAsync({
      to: tx.to as `0x${string}`,
      data: tx.data as `0x${string}`,
      value: BigInt(tx.value || '0'),
      gas: tx.gas ? BigInt(tx.gas) : undefined,
      chainId: tx.chainId,
    });
    return hash;
  };

  const waitStatus = async (cid: number, hash: string) => {
    for (let i = 0; i < 60; i++) {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      try {
        const status = await getTrade(cid, hash);
        if (status.status === 'CONFIRMED' || status.status === 'FAILED' || status.status === 'REPLACED') {
          return status;
        }
      } catch {
        // keep polling
      }
    }
    throw new Error('等待交易确认超时');
  };

  const startLiveSwap = async () => {
    if (!validAmount || insufficient || !bestRoute?.quoteId || !validSlippage || liveBusy.current) return;
    if (!account.address) {
      setQuoteError('请先连接钱包');
      return;
    }
    if (chainId !== TRADE_ALLOWED_CHAIN_ID) {
      setQuoteError('真实兑换仅支持 Base Sepolia，请在顶部切换到 Sepolia');
      return;
    }
    liveBusy.current = true;
    setQuoteError(null);
    setForcedState(null);
    setIsApproving(false);
    setStep(0);
    setTransaction({ from: fromSymbol, to: toSymbol, paid: numFrom, received, hash: '', nonce: nextNonce.current++, commit: true });
    try {
      if (account.chainId !== TRADE_ALLOWED_CHAIN_ID) {
        await switchChainAsync({ chainId: TRADE_ALLOWED_CHAIN_ID });
      }
      let prepared = await prepareTrade({
        quoteId: bestRoute.quoteId,
        userAddress: account.address,
        infiniteApprove: false,
      });
      setStep(1);
      if (prepared.needsApprove && prepared.approveTx) {
        setIsApproving(true);
        const approveHash = await sendUnsigned(prepared.approveTx);
        setTransaction((prev) => (prev ? { ...prev, hash: approveHash } : prev));
        setStep(2);
        await reportTrade({
          chainId: prepared.approveTx.chainId,
          txHash: approveHash,
          kind: 'APPROVE',
          quoteId: prepared.quoteId,
          userAddress: account.address,
        });
        setStep(3);
        const approveStatus = await waitStatus(prepared.approveTx.chainId, approveHash);
        setIsApproving(false);
        if (approveStatus.status !== 'CONFIRMED') {
          throw new Error(approveStatus.revertReason || '授权交易失败');
        }
        // 授权上链后再 prepare：此时才会做 swap eth_call 模拟并带回 gasEstimate
        setStep(1);
        prepared = await prepareTrade({
          quoteId: bestRoute.quoteId,
          userAddress: account.address,
          infiniteApprove: false,
        });
        if (prepared.needsApprove) {
          throw new Error('授权后额度仍不足，请重试或提高授权数量');
        }
      }
      const swapHash = await sendUnsigned(prepared.swapTx);
      setTransaction((prev) => (prev ? { ...prev, hash: swapHash } : prev));
      setStep(2);
      await reportTrade({
        chainId: prepared.swapTx.chainId,
        txHash: swapHash,
        kind: 'SWAP',
        quoteId: prepared.quoteId,
        userAddress: account.address,
      });
      setStep(3);
      const swapStatus = await waitStatus(prepared.swapTx.chainId, swapHash);
      if (swapStatus.status !== 'CONFIRMED') {
        throw new Error(swapStatus.revertReason || '兑换交易失败');
      }
      setStep(4);
      executeRef.current(fromSymbol, toSymbol, numFrom, received, swapHash);
    } catch (err: unknown) {
      setIsApproving(false);
      setForcedState('failed');
      setQuoteError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : '兑换失败');
      setTransaction(null);
      setStep(0);
    } finally {
      liveBusy.current = false;
    }
  };

  const startTransaction = (commit: boolean) => {
    if (!validAmount || insufficient || !bestRoute || !validSlippage || (transaction !== null && step < 4) || isApproving) return;
    if (live) {
      void startLiveSwap();
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
    if ((transaction !== null && step < 4) || isApproving || !validAmount || insufficient || !validSlippage || state === 'quoting') return;
    if (!live && needsApproval) setIsApproving(true);
    else startTransaction(true);
  };

  return {
    fromSymbol, toSymbol, fromAmount, fromToken, toToken, numFrom,
    slippage, setSlippage, customSlippage, setCustomSlippage, effectiveSlippage, validSlippage,
    routes, bestRoute, received, minimumReceived, state, isBusy: (transaction !== null && step < 4) || isApproving, isApproving, step, transaction,
    quoteError, live, updateAmount, selectToken, invert, selectDemoState, act,
  };
}
