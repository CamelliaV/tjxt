package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-09
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final UserClient userClient;
    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;
    private final InteractionReplyMapper replyMapper;
    @Autowired
    private IInteractionReplyService replyService;

    /**
     * 新增提问
     */
    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        Long userId = UserContext.getUser();
        // * 拷贝dto中数据
        InteractionQuestion question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        // * 补全用户id即可保存
        question.setUserId(userId);
        boolean success = save(question);
        if (!success) {
            throw new DbException("新增提问数据失败");
        }
    }

    /**
     * 分页查询问题（用户端）
     */
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        // * 健壮性判断
        if (query == null || (query.getSectionId() == null && query.getCourseId() == null)) {
            throw new BizIllegalException("问题分页查询业务参数缺失");
        }

        // * 分页查询hidden（管理端参数）为false的问题，根据前端条件（是否自己，小节，课程）过滤
        Page<InteractionQuestion> page = lambdaQuery()
                // * 不查详情，懒加载
                .select(InteractionQuestion.class, q -> !q.getProperty()
                                                          .equals("description"))
                .eq(InteractionQuestion::getHidden, false)
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .eq(query.getCourseId() != null, InteractionQuestion::getCourseId, query.getCourseId())
                .eq(BooleanUtils.isTrue(query.getOnlyMine()), InteractionQuestion::getUserId, UserContext.getUser())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> questions = page.getRecords();
        if (CollUtils.isEmpty(questions)) {
            return PageDTO.empty(page);
        }

        // * 根据latestAnswerId批量查询最近一次回答信息
        // * 根据userId批量查询用户信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> latestAnswerIds = new HashSet<>();
        for (InteractionQuestion question : questions) {
            latestAnswerIds.add(question.getLatestAnswerId());
            if (question.getAnonymity()) continue;
            userIds.add(question.getUserId());
        }

        latestAnswerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>();
        // * 不为空再查数据库
        if (CollUtils.isNotEmpty(latestAnswerIds)) {
            List<InteractionReply> replyList = replyMapper.selectBatchIds(latestAnswerIds);
            // * 查数据库不为空再继续
            if (CollUtils.isNotEmpty(replyList)) {
                // * 再将回复中的用户id也加入一并查询，匿名或隐藏的不加
                for (InteractionReply reply : replyList) {
                    if (reply.getHidden()) continue;
                    replyMap.put(reply.getId(), reply);
                    if (reply.getAnonymity()) continue;
                    userIds.add(reply.getUserId());
                }
            }
        }

        userIds.remove(null);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            if (CollUtils.isNotEmpty(userDTOS)) {
                userDTOMap = userDTOS.stream()
                                     .collect(Collectors.toMap(UserDTO::getId, Function.identity()));
            }
        }
        // * 封装VO
        List<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion question : questions) {
            QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
            // * 封装提问者信息
            if (!question.getAnonymity()) {
                UserDTO userDTO = userDTOMap.get(question.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getUsername());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }
            // * 封装回复信息
            InteractionReply reply = replyMap.get(question.getLatestAnswerId());
            if (reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                if (!reply.getAnonymity()) {
                    UserDTO userDTO = userDTOMap.get(reply.getUserId());
                    if (userDTO != null) {
                        vo.setLatestReplyUser(userDTO.getUsername());
                    }
                }
            }

            voList.add(vo);
        }


        return PageDTO.of(page, voList);
    }

    /**
     * id查问题
     */
    @Override
    public QuestionVO queryQuestionById(Long id) {
        // * 数据库查问题
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new DbException("id查询问题失败");
        }
        // * 构造VO，查用户数据，补全VO信息
        QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);
        if (!questionVO.getAnonymity()) {
            UserDTO userDTO = userClient.queryUserById(questionVO.getUserId());
            if (userDTO != null) {
                questionVO.setUserIcon(userDTO.getIcon());
                questionVO.setUserName(userDTO.getUsername());
            }
        }
        return questionVO;
    }

    /**
     * 分页查询问题（管理端）
     */
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // * 处理课程名称，获得课程id
        List<Long> courseIds = new ArrayList<>();
        if (StringUtils.isNotEmpty(query.getCourseName())) {
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            // * 没有课程，对应也就没有问题
            if (CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }
        // * 分页查询问题，根据前端传参过滤
        Page<InteractionQuestion> page = lambdaQuery()
                .gt(query.getBeginTime() != null, InteractionQuestion::getUpdateTime, query.getBeginTime())
                .lt(query.getEndTime() != null, InteractionQuestion::getUpdateTime, query.getEndTime())
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .in(CollUtils.isNotEmpty(courseIds), InteractionQuestion::getCourseId, courseIds)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> questions = page.getRecords();
        // * 数据库里没有问题数据
        if (CollUtils.isEmpty(questions)) {
            return PageDTO.empty(page);
        }

        Set<Long> chapterIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> courseIdSet = new HashSet<>();
        for (InteractionQuestion question : questions) {
            // * 章chapter和节section在一张表中
            chapterIds.add(question.getChapterId());
            chapterIds.add(question.getSectionId());
            courseIdSet.add(question.getCourseId());
            // * 管理后端，不存在匿名不查询
            userIds.add(question.getUserId());
        }
        // * userId查询用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (CollUtils.isNotEmpty(userDTOS)) {
            userDTOMap = userDTOS.stream()
                                 .collect(Collectors.toMap(UserDTO::getId, Function.identity()));
        }
        // * courseId查询课程信息
        List<CourseSimpleInfoDTO> courseList = courseClient.getSimpleInfoList(courseIdSet);
        Map<Long, CourseSimpleInfoDTO> courseMap = new HashMap<>();
        if (CollUtils.isNotEmpty(courseList)) {
            courseMap = courseList.stream()
                                  .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, Function.identity()));
        }
        // * 章节id查询章节信息
        List<CataSimpleInfoDTO> catalogueList = catalogueClient.batchQueryCatalogue(chapterIds);
        Map<Long, CataSimpleInfoDTO> catalogueMap = new HashMap<>();
        if (CollUtils.isNotEmpty(catalogueList)) {
            catalogueMap = catalogueList.stream()
                                        .collect(Collectors.toMap(CataSimpleInfoDTO::getId, Function.identity()));
        }
        // * 遍历封装
        List<QuestionAdminVO> voList = new ArrayList<>();
        for (InteractionQuestion question : questions) {
            QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
            // * 用户
            UserDTO userDTO = userDTOMap.get(question.getUserId());
            if (userDTO != null) {
                vo.setUserName(userDTO.getUsername());
            }
            // * 课程与分类（分类数据来自JVM内存）
            CourseSimpleInfoDTO course = courseMap.get(question.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getName());
                String categoryNames = categoryCache.getCategoryNames(course.getCategoryIds());
                vo.setCategoryName(categoryNames);
            }
            // * 章与节（一张表）
            CataSimpleInfoDTO chapter = catalogueMap.get(question.getChapterId());
            CataSimpleInfoDTO section = catalogueMap.get(question.getSectionId());
            if (chapter != null) {
                vo.setChapterName(chapter.getName());
            }
            if (section != null) {
                vo.setSectionName(section.getName());
            }
            // * 加入结果集
            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }

    /**
     * 更新问题
     */
    @Override
    public void updateQuestionById(Long id, QuestionFormDTO dto) {
        // * 没有要更新的数据
        if (dto == null || id == null) {
            return;
        }
        // ? 如果没有数据也会返回false，没法区分无数据更新与实际数据库操作失败
        lambdaUpdate()
                .eq(InteractionQuestion::getId, id)
                .eq(InteractionQuestion::getUserId, UserContext.getUser())
                .set(dto.getAnonymity() != null, InteractionQuestion::getAnonymity, dto.getAnonymity())
                .set(dto.getTitle() != null, InteractionQuestion::getTitle, dto.getTitle())
                .set(dto.getDescription() != null, InteractionQuestion::getDescription, dto.getDescription())
                .update();
    }

    /**
     * 删除问题
     */
    @Override
    public void deleteQuestionById(Long id) {
        // * 待删除问题不存在
        if (id == null) {
            return;
        }
        Long userId = UserContext.getUser();
        // * 问题必须为当前用户提出的
        InteractionQuestion question = lambdaQuery()
                .eq(InteractionQuestion::getId, id)
                .eq(InteractionQuestion::getUserId, userId)
                .one();
        if (question == null) {
            throw new BizIllegalException("不能删除不是自己的问题");
        }
        // * 删除问题
        boolean success = removeById(id);
        if (!success) {
            throw new DbException("删除用户问题失败");
        }
        // * 删除回复
        replyService.lambdaUpdate()
                    .eq(InteractionReply::getQuestionId, id)
                    .remove();
    }

    /**
     * 更新问题隐藏状态（管理端）
     */
    @Override
    public void updateQuestionHiddenById(Long id, Boolean hidden) {
        // * 无更新信息
        if (id == null || hidden == null) {
            return;
        }

        boolean success = lambdaUpdate()
                .eq(InteractionQuestion::getId, id)
                .set(InteractionQuestion::getHidden, hidden)
                .update();
        if (!success) {
            throw new DbException("更新问题隐藏状态失败");
        }
    }

    /**
     * 根据问题id查询问题
     * 一个接口带你了解CRUD为什么是体力活😅
     */
    @Override
    public QuestionAdminVO queryQuestionByIdAdmin(Long id) {
        // * 不存在待查询数据
        if (id == null) {
            return null;
        }
        InteractionQuestion question = lambdaQuery()
                .eq(InteractionQuestion::getId, id)
                .one();
        // * 数据库不存在此数据
        if (question == null) {
            return null;
        }
        // * 查询过后标记问题状态为已查看
        if (question.getStatus() == QuestionStatus.UN_CHECK) {
            lambdaUpdate()
                    .eq(InteractionQuestion::getId, id)
                    .set(InteractionQuestion::getStatus, QuestionStatus.CHECKED)
                    .update();
        }
        question.setStatus(QuestionStatus.CHECKED);

        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        // * 补全课程相关数据
        if (question.getCourseId() != null) {
            CourseFullInfoDTO course = courseClient.getCourseInfoById(question.getCourseId(), true, true);
            if (course != null) {
                List<Long> teacherIds = course.getTeacherIds();
                // * 健壮性检查，存在对应教师数据
                if (CollUtils.isNotEmpty(teacherIds)) {
                    List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
                    // * 补全教师用户名数据
                    if (CollUtils.isNotEmpty(teachers)) {
                        vo.setTeacherName(teachers.stream()
                                                  .map(UserDTO::getName)
                                                  .collect(Collectors.joining("/")));
                    }
                }
                // * 补全课程名数据
                vo.setCourseName(course.getName());
                // * 补全分类相关数据
                List<Long> categoryIds = course.getCategoryIds();
                if (CollUtils.isNotEmpty(categoryIds)) {
                    String categoryNames = categoryCache.getCategoryNames(categoryIds);
                    if (categoryNames != null) {
                        vo.setCategoryName(categoryNames);
                    }
                }
            }
        }
        // * 补全用户相关数据
        if (question.getUserId() != null) {
            UserDTO user = userClient.queryUserById(question.getUserId());
            if (user != null) {
                vo.setUserName(user.getUsername());
            }
        }
        // * 补全章节相关数据
        List<Long> catalogueIds = new ArrayList<>();
        if (question.getChapterId() != null) {
            catalogueIds.add(question.getChapterId());
        }
        if (question.getSectionId() != null) {
            catalogueIds.add(question.getSectionId());
        }
        if (CollUtils.isNotEmpty(catalogueIds)) {
            List<CataSimpleInfoDTO> catalogueInfoList = catalogueClient.batchQueryCatalogue(catalogueIds);
            if (CollUtils.isNotEmpty(catalogueInfoList)) {
                Map<Long, String> catalogueNameMap = catalogueInfoList.stream()
                                                                      .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
                vo.setChapterName(catalogueNameMap.getOrDefault(question.getChapterId(), ""));
                vo.setSectionName(catalogueNameMap.getOrDefault(question.getSectionId(), ""));
            }
        }

        return vo;
    }
}