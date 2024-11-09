package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.learning.domain.po.LearningLesson;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 学生课程表 Mapper 接口
 * </p>
 *
 * @author CamelliaV
 * @since 2024-10-30
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {

    // * 最近学习的一门课
    @Select("SELECT * FROM tj_learning.learning_lesson WHERE user_id = #{userId} AND status = 1 ORDER BY latest_learn_time DESC LIMIT 0, 1")
    LearningLesson queryCurrent(@Param("userId") Long userId);

    @Select("SELECT SUM(week_freq) FROM tj_learning.learning_lesson WHERE user_id = #{userId} AND plan_status = 1 AND status IN (0, 1)")
    Integer queryWeekFinishedPlan(@Param("userId") Long userId);
}
