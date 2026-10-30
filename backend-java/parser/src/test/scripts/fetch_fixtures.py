#!/usr/bin/env python3
"""Fetch real Base mainnet receipts into parser fixtures.

Usage: python fetch_fixtures.py [target ...]
Targets: v2 aerodrome slipstream multi aggregator (default: all).
Existing fixture files are only overwritten for the targets given.
"""
from __future__ import annotations

import json
import sys
import time
import urllib.request
from pathlib import Path

RPCS = ["https://base-rpc.publicnode.com", "https://mainnet.base.org"]
OUT = Path(__file__).resolve().parents[1] / "resources" / "fixtures"

V2_SWAP = "0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822"
AERO_SWAP = "0xb3e2773606abfd36b5bd91394b3a54d1398336c65005baf7bf7a05efeffaf75b"
CL_SWAP = "0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67"
SWAP_TOPICS = (V2_SWAP, AERO_SWAP, CL_SWAP)
TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
WITHDRAWAL = "0x7fcf532c15f0a6db0bd6d0e038bea71d30d808c7d98cb3bf7268a95bf5081b65"
FACTORY_SEL = "0xc45a0155"
TOKEN0_SEL = "0x0dfe1681"
TOKEN1_SEL = "0xd21220a7"

WETH = "0x4200000000000000000000000000000000000006"
NATIVE = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
V2_FACTORY = "0x8909dc15e40173ff4699343b6eb8132c65e18ec6"
AERO_FACTORY = "0x420dd381b31aef6683db6b902084cb0ffece40da"
V3_FACTORY = "0x33128a8fc17869897dce68ed026d694621f6fdfd"
SLIP_FACTORY = "0x5e7bb104d84c7cb9b682aac2f3d509f5f406809a"
UNIVERSAL = "0x6ff5693b99212da76ad316178a184ab56d299b43"

V2_WETH_USDC = "0x88a43bbdf9d098eec7bceda4e2494615dfd9bb9c"
AERO_WETH_USDC = "0xcdac0d6c6c59727a65f871236188350531885c43"
SLIP_WETH_USDC = "0xb2cc224c1c9fee385f8ad6a55b4d94e92359dc59"
V3_WETH_USDC_500 = "0xd0b53d9277642d899dd5c7cce748381e5d0ecfba"

rpc_index = 0


def rpc(method: str, params: list, retries: int = 8):
    global rpc_index
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}).encode()
    last = None
    for attempt in range(retries):
        endpoint = RPCS[rpc_index % len(RPCS)]
        req = urllib.request.Request(endpoint, data=body, headers={"Content-Type": "application/json", "User-Agent": "Mozilla/5.0"})
        try:
            with urllib.request.urlopen(req, timeout=40) as resp:
                payload = json.load(resp)
            if "error" in payload:
                raise RuntimeError(str(payload["error"]))
            time.sleep(0.35)
            return payload["result"]
        except Exception as ex:
            last = ex
            rpc_index += 1
            time.sleep(1.2 * (attempt + 1))
    raise RuntimeError(f"rpc failed: {last}")


def lower(addr: str | None) -> str | None:
    return None if addr is None else addr.lower()


def topic_address(topic: str) -> str:
    return "0x" + topic[-40:].lower()


def eth_call(to: str, data: str) -> str:
    return topic_address(rpc("eth_call", [{"to": to, "data": data}, "latest"]))


def swap_log_count(receipt: dict) -> int:
    return sum(1 for log in receipt["logs"] if (log.get("topics") or [""])[0].lower() in SWAP_TOPICS)


def enrich(tx_hash: str, max_swaps: int | None = None) -> dict | None:
    print("enrich", tx_hash, flush=True)
    receipt = rpc("eth_getTransactionReceipt", [tx_hash])
    if int(receipt["status"], 16) != 1 or (max_swaps is not None and swap_log_count(receipt) > max_swaps):
        return None
    tx = rpc("eth_getTransactionByHash", [tx_hash])
    factories, tokens = {}, {}
    for log in receipt["logs"]:
        topics = log.get("topics") or []
        pool = lower(log["address"])
        if not topics or topics[0].lower() not in SWAP_TOPICS or pool in factories:
            continue
        factories[pool] = eth_call(pool, FACTORY_SEL)
        tokens[pool] = {"token0": eth_call(pool, TOKEN0_SEL), "token1": eth_call(pool, TOKEN1_SEL)}
    return {"hash": lower(tx["hash"]), "block": int(tx["blockNumber"], 16), "tx": tx, "receipt": receipt,
            "poolFactories": factories, "poolTokens": tokens}


def net_flow(bundle: dict) -> dict:
    """Same rule as SwapNetFlow: Transfer + WETH Withdrawal (src user or tx.to) + tx.value."""
    tx = bundle["tx"]
    user = lower(tx["from"])
    router = lower(tx.get("to"))
    delta: dict[str, int] = {}
    for log in bundle["receipt"]["logs"]:
        topics = [t.lower() for t in log.get("topics") or []]
        address = lower(log["address"])
        if len(topics) == 3 and topics[0] == TRANSFER and len(log["data"]) >= 66:
            src, dst, value = topic_address(topics[1]), topic_address(topics[2]), int(log["data"][:66], 16)
            if src == user and dst != user:
                delta[address] = delta.get(address, 0) - value
            if dst == user and src != user:
                delta[address] = delta.get(address, 0) + value
        if address == WETH and len(topics) >= 2 and topics[0] == WITHDRAWAL and len(log["data"]) >= 66 and topic_address(topics[1]) in (user, router):
            delta[NATIVE] = delta.get(NATIVE, 0) + int(log["data"][:66], 16)
    value = int(tx.get("value") or "0x0", 16)
    if value > 0:
        delta[NATIVE] = delta.get(NATIVE, 0) - value
    return {token: amount for token, amount in delta.items() if amount != 0}


def unique_flow(bundle: dict) -> bool:
    flow = net_flow(bundle)
    return sum(1 for v in flow.values() if v < 0) == 1 and sum(1 for v in flow.values() if v > 0) == 1


def count_swaps(bundle: dict, factory: str, topic: str) -> int:
    n = 0
    for log in bundle["receipt"]["logs"]:
        topics = log.get("topics") or []
        if topics and topics[0].lower() == topic and bundle["poolFactories"].get(lower(log["address"])) == factory:
            n += 1
    return n


def known_factories(bundle: dict) -> set:
    return set(bundle["poolFactories"].values())


def with_expected(bundle: dict) -> dict:
    flow = net_flow(bundle)
    token_in = next(t for t, v in flow.items() if v < 0)
    token_out = next(t for t, v in flow.items() if v > 0)
    bundle.update({"tokenIn": token_in, "tokenOut": token_out, "amountIn": str(-flow[token_in]), "amountOut": str(flow[token_out])})
    return bundle


def pick(pool: str, topic: str, predicate, blocks: int = 150, tries: int = 20, max_swaps: int | None = None) -> dict | None:
    head = int(rpc("eth_blockNumber", []), 16)
    logs = rpc("eth_getLogs", [{"fromBlock": hex(head - blocks), "toBlock": hex(head), "address": pool, "topics": [topic]}])
    print("pool", pool, "logs", len(logs), flush=True)
    seen = set()
    for item in reversed(logs):
        tx_hash = lower(item["transactionHash"])
        if tx_hash in seen:
            continue
        seen.add(tx_hash)
        if len(seen) > tries:
            break
        bundle = enrich(tx_hash, max_swaps)
        if bundle is not None and predicate(bundle):
            return bundle
    return None


def as_pool_token(token: str) -> str:
    return WETH if token == NATIVE else token


def single(factory: str, topic: str):
    """Exactly one verified swap whose pool tokens match the user's unique net in/out."""

    def predicate(bundle: dict) -> bool:
        if count_swaps(bundle, factory, topic) != 1 or not unique_flow(bundle):
            return False
        flow = net_flow(bundle)
        token_in = as_pool_token(next(t for t, v in flow.items() if v < 0))
        token_out = as_pool_token(next(t for t, v in flow.items() if v > 0))
        for pool, tokens in bundle["poolTokens"].items():
            if bundle["poolFactories"].get(pool) != factory:
                continue
            pair = {tokens["token0"], tokens["token1"]}
            if token_in in pair and token_out in pair:
                return True
        return False

    return predicate


TARGETS = {
    "v2": ("v2-single.json", lambda: pick(V2_WETH_USDC, V2_SWAP, single(V2_FACTORY, V2_SWAP), max_swaps=1)),
    "aerodrome": ("aerodrome-single.json", lambda: pick(AERO_WETH_USDC, AERO_SWAP, single(AERO_FACTORY, AERO_SWAP), max_swaps=1)),
    "slipstream": ("slipstream-single.json",
                   lambda: pick(SLIP_WETH_USDC, CL_SWAP, single(SLIP_FACTORY, CL_SWAP), blocks=1500, tries=300, max_swaps=1)),
    "multi": ("multi-hop.json", lambda: pick(V3_WETH_USDC_500, CL_SWAP,
                                             lambda b: count_swaps(b, V3_FACTORY, CL_SWAP) >= 2 and unique_flow(b), blocks=300, tries=30)),
    "aggregator": ("aggregator-universal.json", lambda: pick(V3_WETH_USDC_500, CL_SWAP,
                                                          lambda b: lower(b["tx"].get("to")) == UNIVERSAL and unique_flow(b), blocks=400, tries=30)),
}


def main(names: list[str]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name in names or list(TARGETS):
        file, fetch = TARGETS[name]
        bundle = fetch()
        if bundle is None:
            print("no match for", name, flush=True)
            continue
        (OUT / file).write_text(json.dumps(with_expected(bundle), indent=2), encoding="utf-8")
        print("wrote", file, bundle["hash"], flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
