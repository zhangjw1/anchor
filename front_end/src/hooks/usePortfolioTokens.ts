import { useCallback, useEffect, useState } from 'react';
import { ApiError, getAssets } from '../api/anchor';
import { isMockMode } from '../config/env';
import { INITIAL_TOKENS } from '../mock/initialData';
import { Token } from '../types';
import { fromRawAmount, fromUsdMicro } from '../utils/amount';

const NATIVE = '0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee';

function cloneInitial(): Record<string, Token> {
  return Object.fromEntries(Object.entries(INITIAL_TOKENS).map(([key, token]) => [key, { ...token }]));
}

/** 真实模式拉取资产并映射到现有 Token 结构；mock 模式返回初始余额。 */
export function usePortfolioTokens(chainId: number, address: string | undefined) {
  const [tokens, setTokens] = useState<Record<string, Token>>(cloneInitial);
  const [netWorthUsd, setNetWorthUsd] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (isMockMode() || !address) {
      setTokens(cloneInitial());
      setNetWorthUsd(null);
      setError(null);
      return;
    }
    setLoading(true);
    try {
      const extra = Object.values(INITIAL_TOKENS)
        .map((token) => token.address.toLowerCase())
        .filter((item) => item && item !== '0x0000000000000000000000000000000000000000');
      const portfolio = await getAssets(chainId, address, extra);
      const next: Record<string, Token> = {};
      for (const holding of portfolio.holdings) {
        const addressLower = holding.token.address.toLowerCase();
        const isNative = addressLower === NATIVE;
        const matched = Object.values(INITIAL_TOKENS).find((token) =>
          isNative
            ? token.symbol === 'ETH'
            : token.address.toLowerCase() === addressLower,
        );
        const symbol = holding.token.symbol || matched?.symbol || holding.token.address.slice(0, 6);
        const decimals = holding.token.decimals ?? matched?.decimals ?? 18;
        next[symbol] = {
          symbol,
          name: matched?.name || symbol,
          balance: fromRawAmount(holding.balance, decimals),
          decimals,
          priceUsd: holding.priceUsd == null ? (matched?.priceUsd ?? 0) : fromUsdMicro(holding.priceUsd),
          address: isNative ? (matched?.address || holding.token.address) : holding.token.address,
        };
      }
      // 保留已知但余额为 0 的展示代币，避免选择器变空
      for (const [symbol, token] of Object.entries(INITIAL_TOKENS)) {
        if (!next[symbol]) {
          next[symbol] = { ...token, balance: 0 };
        }
      }
      setTokens(next);
      setNetWorthUsd(portfolio.netWorthUsd == null ? null : fromUsdMicro(portfolio.netWorthUsd));
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '资产加载失败');
    } finally {
      setLoading(false);
    }
  }, [address, chainId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { tokens, setTokens, netWorthUsd, error, loading, refresh };
}
