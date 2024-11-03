package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-10-30
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;

    /**
     * 添加课程到对应用户课表
     */
    @Override
    public void addLesson(OrderBasicDTO dto) {
        // * 根据课程id查询对应课程信息（有效期）
        List<Long> courseIds = dto.getCourseIds();
        List<CourseSimpleInfoDTO> courseList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseList)) {
            throw new DbException("课程数据不存在");
        }

        // * 遍历封装课程信息到对应课表中
        List<LearningLesson> lessonList = new ArrayList<>();
        for (CourseSimpleInfoDTO course : courseList) {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(dto.getUserId())
                  .setCourseId(course.getId())
                  .setExpireTime(LocalDateTime.now()
                                              .plusMonths(course.getValidDuration()));

            lessonList.add(lesson);
        }

        // * 批量保存至数据库
        saveBatch(lessonList);
    }

    /**
     * 分页查询我的课表
     */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        // * 分页查询当前用户课表信息
        Long userId = UserContext.getUser();
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<LearningLesson> lessonList = page.getRecords();
        if (CollUtils.isEmpty(lessonList)) {
            return PageDTO.empty(page);
        }

        // * 根据课程id获取课程信息
        List<Long> courseIds = lessonList.stream()
                                         .map(LearningLesson::getCourseId)
                                         .collect(Collectors.toList());

        List<CourseSimpleInfoDTO> courseList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseList)) {
            throw new DbException("课程信息不存在");
        }

        // * 课程id为key，课程对象为value封装对应课程信息
        Map<Long, CourseSimpleInfoDTO> courseMap = courseList.stream()
                                                             .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        List<LearningLessonVO> voList = new ArrayList<>();

        // * 遍历课表封装结果
        for (LearningLesson lesson : lessonList) {
            LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);

            CourseSimpleInfoDTO course = courseMap.get(vo.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getName());
                vo.setCourseCoverUrl(course.getCoverUrl());
                vo.setSections(course.getSectionNum());
            }

            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }

    /**
     * 查询上次学习的一门课相关信息
     */
    @Override
    public LearningLessonVO queryCurrent() {
        // * 查询最近学习的一门课
        Long userId = UserContext.getUser();
        LearningLesson lesson = getBaseMapper().queryCurrent(userId);
        if (lesson == null) {
            return null;
        }

        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        // * 课程id查课程信息
        CourseFullInfoDTO course = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (course == null) {
            throw new DbException("课程数据不存在");
        }

        vo.setCourseName(course.getName());
        vo.setCourseCoverUrl(course.getCoverUrl());
        vo.setSections(course.getSectionNum());

        // * 统计课表中课程数量
        Integer courseAmount = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();
        vo.setCourseAmount(courseAmount);

        // * 根据最近一次学习小节id查询小节信息
        Long latestSectionId = lesson.getLatestSectionId();
        if (latestSectionId == null || latestSectionId == 0) {
            return null;
        }

        List<CataSimpleInfoDTO> cataList = catalogueClient.batchQueryCatalogue(List.of(latestSectionId));
        if (CollUtils.isEmpty(cataList)) {
            return null;
        }
        // * 封装
        CataSimpleInfoDTO cata = cataList.get(0);
        if (cata == null) {
            return null;
        }

        vo.setLatestSectionName(cata.getName());
        vo.setLatestSectionIndex(cata.getCIndex());

        return vo;
    }

    /**
     * 根据课程id查相关信息
     */
    @Override
    public LearningLessonVO queryByCourseId(Long courseId) {
        // * 用户id，课程id唯一查出课表
        Long userId = UserContext.getUser();

        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getUserId, userId)
                .one();

        if (lesson == null) {
            return null;
        }
        // * 课程id查课程信息
        CourseFullInfoDTO course = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (course == null) {
            throw new DbException("课程信息不存在");
        }
        // * 封装vo
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(course.getName());
        vo.setCourseCoverUrl(course.getCoverUrl());
        vo.setSections(course.getSectionNum());

        return vo;
    }


    /**
     * 查询是否购买了某课程
     */
    @Override
    public Long isLessonValid(Long courseId) {
        // * 健壮性检查
        if (courseId == null) {
            return null;
        }
        // * 查询对应课表项（用户id，课程id）唯一确定
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

        // * 未购买此课程，返回
        if (lesson == null) {
            return null;
        }
        // * 已购买，返回对应课表id
        return lesson.getId();
    }

    /**
     * 统计当前课程对应学习人数(统计对应课程id的learninglesson条目数)
     */
    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        if (courseId == null) {
            return null;
        }
        Integer count = lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .count();
        return count;
    }

    /**
     * 删除对应用户的对应课程（userId来源于MQ或上下文）
     */
    @Override
    public void deleteLessonByCourse(Long userId, Long courseId) {
        // * 健壮性保障
        if (courseId == null) {
            return;
        }
        // * MQ中确保userId不为null,所以此时必为controller直接调用
        if (userId == null) {
            userId = UserContext.getUser();
        }
        // * 根据唯一标识(userId, courseId)删除对应课表项
        remove(new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId));
    }

}
