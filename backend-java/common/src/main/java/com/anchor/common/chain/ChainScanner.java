package com.anchor.common.chain;
/**
 * 区块扫描循环。
 */
public interface ChainScanner {
    /**
     * 从检查点一直扫到进程停止；暂停时在循环内等待，可被 resume 唤醒。
     */
    void scanForever();

    /**
     * 解除暂停，继续从当前检查点扫描。chainId 须匹配本实例配置的链。
     */
    void resume(long chainId);

    /** 是否因无法定位重组祖先等原因处于暂停。 */
    boolean isPaused();
}
