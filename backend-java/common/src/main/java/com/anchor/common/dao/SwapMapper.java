package com.anchor.common.dao;

import com.anchor.common.entity.SwapDO;
import com.anchor.common.dto.SwapDTO;
import com.anchor.common.query.SwapQuery;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 交换记录的写入、分页和确认状态更新。
 */
@Mapper
public interface SwapMapper {
    /**
     * 按链、交易哈希和日志序号幂等写入。
     */
    int saveBatch(@Param("list") List<SwapDO> list);
    /**
     * 带出代币符号和精度的分页查询。
     */

    IPage<SwapDTO> listByQuery(IPage<SwapDTO> page, @Param("query") SwapQuery query);
    /**
     * 统计一条链上的交换条数。
     */
    long countByChain(@Param("chainId") long chainId);
    /**
     * 取出尚未重组、且不超过当前链头的交换，用于推进确认状态。
     */

    List<SwapDO> listActive(@Param("chainId") long chainId, @Param("head") long head);
    /**
     * 更新单笔交换的确认状态。
     */
    int updateStatus(@Param("chainId") long chainId, @Param("txHash") String txHash, @Param("logIndex") int logIndex,
                     @Param("status") String status, @Param("actor") String actor);

    /**
     * 把分叉点之后的交换标成已重组，不物理删除。
     */
    int markReorgedAfter(@Param("chainId") long chainId, @Param("ancestor") long ancestor,
                         @Param("status") String status, @Param("actor") String actor);

    /**
     * 按幂等键 (txHash, logIndex) 批量查主键，用于写 hop；只填充 id、txHash、logIndex。
     */
    List<SwapDO> listIdsByKeys(@Param("chainId") long chainId, @Param("keys") List<SwapDO> keys);
}
