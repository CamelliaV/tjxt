package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.Constant;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-09
 */
@Service
@RequiredArgsConstructor
//@RefreshScope
@Slf4j
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;
    private final UserClient userClient;
    private final RemarkClient remarkClient;

    @Value(Constant.CONFIG_BIZTYPE_QA)
    private String bizType;

//    @PostConstruct
//    public void testReadRemoteConfig() {
//        System.out.println(">>>>>>BIZTYPE: " + bizType);
//    }

    /**
     * 新增评论
     */
    @Override
    @Transactional
    public void saveReply(ReplyDTO dto) {
        // * 健壮性校验
        Long questionId = dto.getQuestionId();
        Long answerId = dto.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BizIllegalException("问题id和回答id不可同时为空");
        }
        // * 补全评论者id
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(UserContext.getUser());
        // * 保存评论
        boolean success = save(reply);
        if (!success) {
            throw new DbException("新增回复/评论失败");
        }
        // * 是否是评论
        // * 是评论（问题下二级）
        if (answerId != null) {
            success = lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, reply.getAnswerId())
                    .update();
            if (!success) {
                throw new DbException("新增评论更新评论表失败");
            }
        }

        // * 不是评论，属于回复（问题下一级）
        questionService.lambdaUpdate()
                       .set(answerId == null, InteractionQuestion::getLatestAnswerId, reply.getId())
                       .setSql(answerId == null, "answer_times = answer_times + 1")
                       .set(BooleanUtils.isTrue(dto.getIsStudent()), InteractionQuestion::getStatus, QuestionStatus.UN_CHECK)
                       .eq(InteractionQuestion::getId, reply.getQuestionId())
                       .update();
    }

    /**
     * 分页查询回答/评论
     */
    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean isAdmin) {
        // * 健壮性校验
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BizIllegalException("问题id和回答id不可同时为空");
        }

        // * 查询数据库
        Page<InteractionReply> page = lambdaQuery()
                .eq(InteractionReply::getAnswerId, answerId == null ? 0L : answerId)
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(!isAdmin, InteractionReply::getHidden, false)
                .page(query.toMpPage(new OrderItem(Constant.DATA_FIELD_NAME_LIKED_TIME, false), new OrderItem(Constant.DATA_FIELD_NAME_CREATE_TIME, true)));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // * 填充信息，构造用户id（对评论而言是部分）集合，目标回答id集合查询
        Set<Long> targetReplies = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (InteractionReply record : records) {
            // * 管理端或未匿名
            if (isAdmin || !record.getAnonymity()) {
                userIds.add(record.getUserId());
            }
            // * 评论特有
            if (answerId != null) {
                targetReplies.add(record.getTargetReplyId());
            }
        }
        // * 去除不带目标用户引入的null值
        targetReplies.remove(null);
        // * 查询目标回答，获取目标用户id（非匿名部分）
        List<InteractionReply> targetReplyList = new ArrayList<>();
        // * 查评论情况下才查数据库
        if (answerId != null) {
            targetReplyList = listByIds(targetReplies);
        }
        if (CollUtils.isNotEmpty(targetReplyList)) {
            for (InteractionReply reply : targetReplyList) {
                // * 非管理端且匿名或隐藏不加入
                if (!isAdmin && (reply.getAnonymity() || reply.getHidden())) continue;
                userIds.add(reply.getTargetUserId());
            }
        }
        // * 根据完整的用户id集合查询用户信息
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = users.stream()
                                          .collect(Collectors.toMap(UserDTO::getId, Function.identity()));
        // * 查询点赞过的replyIds
        List<Long> likedReplyIds = records.stream()
                                          .map(InteractionReply::getId)
                                          .collect(Collectors.toList());
        Set<Long> likedReplyIdSet = remarkClient.queryLikedListByUserIdsAndBizIds(bizType, likedReplyIds);
        // * 补全vo信息
        List<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            Long userId = record.getUserId();
            UserDTO user = userMap.get(userId);
            // * 匿名不加入信息
            if (user != null) {
                vo.setUserName(user.getUsername());
                vo.setUserIcon(user.getIcon());
                vo.setUserType(user.getType());
            }
            // * 评论补全目标用户信息
            if (answerId != null) {
                UserDTO targetUser = userMap.get(record.getTargetUserId());
                // * 匿名下返回null
                if (targetUser != null) {
                    vo.setTargetUserName(targetUser.getUsername());
                }
            }
            // * 设置点赞高亮
            if (likedReplyIdSet.contains(record.getId())) {
                vo.setLiked(true);
            }
            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }

    /**
     * 修改评论显示状态（管理端）
     */
    @Override
    public void updateReplyHiddenById(Long id, Boolean hidden) {
        // * 无状态更新
        if (hidden == null) {
            return;
        }
        // * 如果隐藏了回答，由于评论是懒加载，隐藏的回答点不进触发评论的加载，也就间接隐藏了
        // * 回答的隐藏不应修改评论的隐藏，否则此操作不可恢复
        lambdaUpdate()
                .eq(InteractionReply::getId, id)
                .set(InteractionReply::getHidden, hidden)
                .update();
    }
}
