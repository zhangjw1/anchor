/** 人类可读数量 → 最小单位整数字符串。 */
export function toRawAmount(human: string | number, decimals: number): string {
  const text = String(human).trim();
  if (!text || text === '.' || Number(text) < 0 || !Number.isFinite(Number(text))) {
    return '0';
  }
  const [wholePart, fractionPart = ''] = text.split('.');
  const whole = wholePart.replace(/^0+(?=\d)/, '') || '0';
  const fraction = (fractionPart + '0'.repeat(decimals)).slice(0, decimals);
  const raw = `${whole}${fraction}`.replace(/^0+(?=\d)/, '') || '0';
  return raw;
}

/** 最小单位整数 → 人类可读 number（展示用）。 */
export function fromRawAmount(raw: string, decimals: number | null | undefined): number {
  const scale = decimals ?? 18;
  const negative = raw.startsWith('-');
  const digits = (negative ? raw.slice(1) : raw).replace(/^0+/, '') || '0';
  const padded = digits.padStart(scale + 1, '0');
  const whole = padded.slice(0, padded.length - scale);
  const fraction = padded.slice(padded.length - scale).replace(/0+$/, '');
  const value = Number(fraction ? `${whole}.${fraction}` : whole);
  const signed = negative ? -value : value;
  return Number.isFinite(signed) ? signed : 0;
}

/** 微美元（6 位）→ USD number。 */
export function fromUsdMicro(micro: string | null | undefined): number {
  if (micro == null) {
    return 0;
  }
  return fromRawAmount(micro, 6);
}

/** 滑点百分比 → 基点。0.5% → 50。 */
export function slippagePercentToBps(percent: number): number {
  return Math.round(percent * 100);
}

/** Uniswap fee tier → 展示用百分比。500 → 0.05。 */
export function feeTierToPercent(fee: number): number {
  return fee / 10_000;
}
