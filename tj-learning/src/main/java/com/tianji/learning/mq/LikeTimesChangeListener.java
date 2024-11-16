package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author CamelliaV
 * @since 2024/11/13 / 22:33
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LikeTimesChangeListener {

    private final IInteractionReplyService replyService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = "qa.liked.times.queue", durable = "true"),
                    exchange = @Exchange(name = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
                    key = MqConstants.Key.QA_LIKED_TIMES_KEY
            )
    )
    public void listenLikeTimesChange(List<LikedTimesDTO> dtoList) {
        // * 健壮性检查
        if (CollUtils.isEmpty(dtoList)) {
            log.error("LikedTimesDTO消息数据有误");
            return;
        }
        // * 封装更新数据
        List<InteractionReply> replyList = new ArrayList<>();
        for (LikedTimesDTO dto : dtoList) {
            InteractionReply reply = new InteractionReply();
            reply.setId(dto.getBizId());
            reply.setLikedTimes(dto.getLikedTimes());
            replyList.add(reply);
        }
        // * 更新数据库
        replyService.updateBatchById(replyList);

    }
//    public void listenLikeTimesChange(List<LikedTimesDTO> dtoList) {
//        // * 健壮性检查
//        if (CollUtils.isEmpty(dtoList)) {
//            log.error("LikedTimesDTO消息数据有误");
//            return;
//        }
//        // * 构造map根据业务id查询新增点赞数
//        Map<Long, Integer> replyUpdateMap = dtoList.stream()
//                                                   .collect(Collectors.toMap(LikedTimesDTO::getBizId, LikedTimesDTO::getLikedTimes));
//        // * 单个消息内不存在重复，Set不是必要
//        Set<Long> replyIdSet = dtoList.stream()
//                                      .map(LikedTimesDTO::getBizId)
//                                      .collect(Collectors.toSet());
//        // ! 先查次数后更新，两次sql
//        List<InteractionReply> replyList = replyService.listByIds(replyIdSet);
//        // * 用于传递数据库更新的list
//        List<InteractionReply> replyUpdateList = new ArrayList<>();
//        if (CollUtils.isEmpty(replyList)) {
//            throw new DbException("数据库缺少待更新评论记录");
//        }
//
//        for (InteractionReply reply : replyList) {
//            InteractionReply replyUpdate = new InteractionReply();
//            replyUpdate.setId(reply.getId());
//            replyUpdate.setLikedTimes(replyUpdateMap.get(reply.getId()) + reply.getLikedTimes());
//            replyUpdateList.add(replyUpdate);
//        }
//        // * 更新数据库
//        replyService.updateBatchById(replyUpdateList);
//        
//    }
}
