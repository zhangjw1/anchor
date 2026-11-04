import { useEffect, useRef, useState } from 'react';
import { fetchChainMetrics } from '../api/anchor';

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

const REST_POLL_MS = 3_000;

function wsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

function num(value: unknown, fallback = 0): number {
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function fromPayload(payload: Record<string, unknown>, connected: boolean): ChainMetrics {
  return {
    headBlock: num(payload.headBlock),
    processedBlock: num(payload.processedBlock ?? payload.blockNumber),
    lagBlocks: num(payload.lagBlocks),
    rpcQps: num(payload.rpcQps),
    rpcErrorRate: num(payload.rpcErrorRate),
    kafkaLag: payload.kafkaLag == null ? null : num(payload.kafkaLag),
    reorgCount: num(payload.reorgCount),
    reorgDepth: num(payload.reorgDepth),
    connected,
  };
}

/**
 * 订阅后端 METRICS；WS 断开时用 REST 兜底，保证头块/已扫与列表同源。
 */
export function useChainMetrics(chainId: number, enabled: boolean): ChainMetrics {
  const [metrics, setMetrics] = useState<ChainMetrics>(EMPTY);
  const chainIdRef = useRef(chainId);
  const wsConnectedRef = useRef(false);
  chainIdRef.current = chainId;

  useEffect(() => {
    if (!enabled) {
      setMetrics(EMPTY);
      wsConnectedRef.current = false;
      return;
    }
    let closed = false;
    let socket: WebSocket | null = null;
    let retryTimer: number | undefined;
    let restTimer: number | undefined;

    const applyRest = async () => {
      try {
        const view = await fetchChainMetrics(chainIdRef.current);
        if (closed || wsConnectedRef.current) {
          return;
        }
        setMetrics(fromPayload(view as unknown as Record<string, unknown>, false));
      } catch {
        // ignore transient REST failures
      }
    };

    const connect = () => {
      socket = new WebSocket(wsUrl());
      socket.onopen = () => {
        if (closed) {
          return;
        }
        wsConnectedRef.current = true;
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
          wsConnectedRef.current = true;
          setMetrics(fromPayload(payload, true));
        } catch {
          // ignore malformed frames
        }
      };
      socket.onclose = () => {
        if (closed) {
          return;
        }
        wsConnectedRef.current = false;
        setMetrics((prev) => ({ ...prev, connected: false }));
        void applyRest();
        retryTimer = window.setTimeout(connect, 3000);
      };
      socket.onerror = () => {
        socket?.close();
      };
    };

    connect();
    void applyRest();
    restTimer = window.setInterval(() => {
      if (!wsConnectedRef.current) {
        void applyRest();
      }
    }, REST_POLL_MS);

    return () => {
      closed = true;
      wsConnectedRef.current = false;
      if (retryTimer) {
        window.clearTimeout(retryTimer);
      }
      if (restTimer) {
        window.clearInterval(restTimer);
      }
      socket?.close();
    };
  }, [enabled, chainId]);

  return metrics;
}
