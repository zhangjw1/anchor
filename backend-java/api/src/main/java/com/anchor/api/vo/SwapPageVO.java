package com.anchor.api.vo;

import com.anchor.common.dto.SwapDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 交换分页结果。
 */
public record SwapPageVO(List<SwapVO> items, long page, long size, long total) {
    /**
     * 把查询页转换成接口返回对象。
     */
    public static SwapPageVO from(IPage<SwapDTO> result) {
        List<SwapVO> items = result.getRecords().stream().map(SwapVO::from).toList();
        return new SwapPageVO(items, result.getCurrent(), result.getSize(), result.getTotal());
    }
}
