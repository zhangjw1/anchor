package com.anchor.api.service;

import com.anchor.api.vo.ChainVO;

import java.util.List;

/**
 * 链列表查询。
 */
public interface ChainQueryService {
    /**
     * 返回已加载的全部链。
     */

    List<ChainVO> list();
}
