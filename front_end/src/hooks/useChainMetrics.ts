import { useEffect, useRef, useState } from 'react';

export interface ChainMetrics {
  headBlock: number;
  processedBlock: number;
  lagBlocks: number;
  rpcQps: number;
  rpcErrorRate: number;
  kafkaLag: number | null;
  reorgCount: number;
  reorgDepth: number;
  connected: boolean;
}

const EMPTY: ChainMetrics = {
  headBlock: 0,
  processedBlock: 0,
  lagBlocks: 0,
  rpcQps: 0,
  rpcErrorRate: 0,
  kafkaLag: null,
  reorgCount: 0,
  reorgDepth: 0,
  connected: false,
};

function wsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

function num(value: unknown, fallback = 0): number {
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : fallback;
}

/**
 * 订阅后端 METRICS 频道，驱动 Header 落后块数与 Footer 状态栏。
 */
export function useChainMetrics(chainId: number, enabled: boolean): ChainMetrics {
  const [metrics, setMetrics] = useState<ChainMetrics>(EMPTY);
  const chainIdRef = useRef(chainId);
  chainIdRef.current = chainId;

  useEffect(() => {
    if (!enabled) {
      setMetrics(EMPTY);
      return;
    }
    let closed = false;
    let socket: WebSocket | null = null;
    let retryTimer: number | undefined;
    let pingTimer: number | undefined;

    const connect = () => {
      socket = new WebSocket(wsUrl());
      socket.onopen = () => {
        if (closed) {
          return;
        }
        setMetrics((prev) => ({ ...prev, connected: true }));
        socket?.send(JSON.stringify({
          action: 'subscribe',
          channel: 'metrics',
          chainId: chainIdRef.current,
        }));
      };
      socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(String(event.data)) as Record<string, unknown>;
          if (payload.type === 'PING') {
            socket?.send(JSON.stringify({ action: 'pong' }));
            return;
          }
          if (payload.type !== 'METRICS' || Number(payload.chainId) !== chainIdRef.current) {
            return;
          }
          setMetrics({
            headBlock: num(payload.headBlock),
            processedBlock: num(payload.processedBlock ?? payload.blockNumber),
            lagBlocks: num(payload.lagBlocks),
            rpcQps: num(payload.rpcQps),
            rpcErrorRate: num(payload.rpcErrorRate),
            kafkaLag: payload.kafkaLag == null ? null : num(payload.kafkaLag),
            reorgCount: num(payload.reorgCount),
            reorgDepth: num(payload.reorgDepth),
            connected: true,
          });
        } catch {
          // ignore malformed frames
        }
      };
      socket.onclose = () => {
        if (closed) {
          return;
        }
        setMetrics((prev) => ({ ...prev, connected: false }));
        retryTimer = window.setTimeout(connect, 3000);
      };
      socket.onerror = () => {
        socket?.close();
      };
    };

    connect();
    return () => {
      closed = true;
      if (retryTimer) {
        window.clearTimeout(retryTimer);
      }
      if (pingTimer) {
        window.clearTimeout(pingTimer);
      }
      socket?.close();
    };
  }, [enabled, chainId]);

  return metrics;
}
