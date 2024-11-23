package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.enums.PointsRecordType;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 Mapper 接口
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-18
 */
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    @Select("SELECT SUM(points) FROM tj_learning.points_record WHERE user_id = #{userId} AND type = #{type} AND create_time >= #{start} AND create_time <= #{end}")
    Integer queryUserTodayPoints(@Param("userId") Long userId, @Param("type") PointsRecordType type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT type, SUM(points) points FROM tj_learning.points_record WHERE user_id = #{userId} AND create_time >= #{start} AND create_time <= #{end} GROUP BY type")
    List<PointsRecord> queryMyPointsToday(Long userId, LocalDateTime start, LocalDateTime end);


    // * 积分记录月初分表存档相关
    // * 分段删除方案
    @Insert("CREATE TABLE IF NOT EXISTS tj_learning.points_record_${seasonId} LIKE tj_learning.points_record")
    void createPointsRecordShardingTable(@Param("seasonId") String seasonId);

    @Insert("INSERT INTO tj_learning.points_record_${seasonId} SELECT * FROM tj_learning.points_record")
    Integer insertAllToShardingTable(@Param("seasonId") String seasonId);

    @Select("SELECT MAX(id), MIN(id) FROM tj_learning.points_record")
    List<Long> queryMaxMinId();

    @Delete("DELETE FROM tj_learning.points_record WHERE id >= #{minId} AND id <= #{maxId} LIMIT #{limit}")
    Integer deletePointsRecordWithRange(@Param("minId") Long minId, @Param("maxId") Long maxId, @Param("limit") int limit);

    // * 重命名方案
    @Update("RENAME TABLE tj_learning.points_record TO tj_learning.points_record_${seasonId}")
    void renamePointsRecordTableToSharding(@Param("seasonId") String seasonId);

    @Insert("CREATE TABLE IF NOT EXISTS tj_learning.points_record LIKE tj_learning.points_record_${seasonId}")
    void copyPointsRecordShardingTableDefinition(@Param("seasonId") String seasonId);
}
