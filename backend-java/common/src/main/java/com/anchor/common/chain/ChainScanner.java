package com.anchor.common.chain;
/**
 * 区块扫描循环。
 */
public interface ChainScanner {
    /**
     * 从检查点一直扫到进程停止。
     */
    void scanForever();
}
