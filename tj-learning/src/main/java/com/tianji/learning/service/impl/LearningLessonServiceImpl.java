package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
    private final LearningRecordMapper recordMapper;
    private final CatalogueClient catalogueClient;

    public static void main(String[] args) {
        System.out.println(LocalDate.now());
        System.out.println(DateUtils.getWeekBeginTime(LocalDate.now()));
        // * 测试结果：修改工具类
        System.out.println(DateUtils.getWeekEndTime(LocalDate.now()));

    }

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
        boolean success = saveBatch(lessonList);
        if (!success) {
            throw new DbException("保存课表失败");
        }
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
        boolean success = remove(new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId));
        if (!success) {
            throw new DbException("删除课表失败");
        }
    }

    /**
     * 添加学习计划
     */
    @Override
    public void createLessonPlan(LearningPlanDTO dto) {

        // * 结合dto课程id与userId查课表id更新
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();

        if (lesson == null) {
            throw new DbException("课表信息不存在");
        }

        boolean success = lambdaUpdate()
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .set(LearningLesson::getWeekFreq, dto.getFreq())
                .eq(LearningLesson::getId, lesson.getId())
                .update();

        if (!success) {
            throw new DbException("课表计划部分 更新失败");
        }
    }

    /**
     * 查询学习计划
     */
    @Override
    public LearningPlanPageVO queryMyPlan(PageQuery pageQuery) {
        LearningPlanPageVO vo = new LearningPlanPageVO();
        // * 获得周开始时间与结束时间
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);

        Long userId = UserContext.getUser();

        // * 统计本周计划数据
        Integer count = recordMapper.selectCount(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, userId)
                        .eq(LearningRecord::getFinished, true)
                        .gt(LearningRecord::getFinishTime, weekBeginTime)
                        .lt(LearningRecord::getFinishTime, weekEndTime)
        );
        vo.setWeekFinished(count);
        // * 查询本周计划学习总数(课表)
        Integer weekFinishedPlan = getBaseMapper().queryWeekFinishedPlan(userId);
        vo.setWeekTotalPlan(weekFinishedPlan);

        // * 查询用户课程学习计划(LearningPlanVO)列表
        // * 分页查询当前用户的课表
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));

        List<LearningLesson> lessonList = page.getRecords();
        // * 没有学习计划
        if (CollUtils.isEmpty(lessonList)) {
            return vo.emptyPage(page);
        }
        // * 课程id查询课程信息
        List<Long> courseIds = lessonList.stream()
                                         .map(LearningLesson::getCourseId)
                                         .collect(Collectors.toList());
        List<CourseSimpleInfoDTO> courseList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseList)) {
            throw new DbException("课程信息缺失");
        }
        Map<Long, CourseSimpleInfoDTO> courseMap = courseList.stream()
                                                             .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, Function.identity()));
        // * 查询对应用户的所有课程对应本周学习小节数
        List<IdAndNumDTO> idAndNumDTOS = recordMapper.countWeekLearnedSections(userId, weekBeginTime, weekEndTime);
        Map<Long, Integer> idAndNumMap = IdAndNumDTO.toMap(idAndNumDTOS);
        // * 封装数据
        List<LearningPlanVO> planVOList = new ArrayList<>();
        for (LearningLesson lesson : lessonList) {
            LearningPlanVO planVO = BeanUtils.copyBean(lesson, LearningPlanVO.class);
            if (planVO == null) {
                throw new DbException("课表数据异常");
            }
            planVO.setWeekLearnedSections(idAndNumMap.getOrDefault(planVO.getId(), 0));
            CourseSimpleInfoDTO course = courseMap.get(planVO.getCourseId());
            planVO.setSections(course.getSectionNum());
            planVO.setCourseName(course.getName());
            planVOList.add(planVO);
        }
        return vo.pageInfo(page.getTotal(), page.getPages(), planVOList);
    }

    /**
     * 定期检查并更新过期课程
     */
    @Override
    public void checkAndExpireLessons() {
        // * 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        // * 更新课程信息
        lambdaUpdate()
                .ne(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .lt(LearningLesson::getExpireTime, now)
                .set(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .update();
    }

}
