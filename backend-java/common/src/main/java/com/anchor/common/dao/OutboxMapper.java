package com.anchor.common.dao;

import com.anchor.common.entity.OutboxEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kafka 事务发件箱。
 */
@Mapper
public interface OutboxMapper {

    int insertBatch(@Param("list") List<OutboxEventDO> list);

    List<OutboxEventDO> listPending(@Param("limit") int limit, @Param("now") OffsetDateTime now);

    int markSent(@Param("id") long id, @Param("actor") String actor, @Param("sentAt") OffsetDateTime sentAt);

    int markFailed(@Param("id") long id, @Param("attempts") int attempts,
                   @Param("nextAttemptAt") OffsetDateTime nextAttemptAt, @Param("actor") String actor);

    long countPending();

    int deleteSentBefore(@Param("before") OffsetDateTime before);
}
