package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.learning.domain.po.LearningRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 Mapper 接口
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-03
 */
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    @Select("SELECT lesson_id id, COUNT(*) num FROM tj_learning.learning_record WHERE user_id = #{userId} AND finished = 1 AND finish_time <= #{weekEndTime} AND finish_time >= #{weekBeginTime} GROUP BY lesson_id")
    List<IdAndNumDTO> countWeekLearnedSections(@Param("userId") Long userId, @Param("weekBeginTime") LocalDateTime weekBeginTime, @Param("weekEndTime") LocalDateTime weekEndTime);

}
