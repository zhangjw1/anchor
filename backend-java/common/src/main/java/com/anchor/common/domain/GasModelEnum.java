package com.anchor.common.domain;
/**
 * 手续费模型。OP 栈链还要额外计算 L1 数据费。
 */
public enum GasModelEnum {
    EIP1559,
    EIP1559_OP_L1_FEE
}
