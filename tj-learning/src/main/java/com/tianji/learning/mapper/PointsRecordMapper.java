package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.enums.PointsRecordType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
