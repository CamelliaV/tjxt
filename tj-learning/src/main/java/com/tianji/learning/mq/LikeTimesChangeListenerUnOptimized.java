package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author CamelliaV
 * @since 2024/11/13 / 22:33
 */
//@Component
@Slf4j
@RequiredArgsConstructor
public class LikeTimesChangeListenerUnOptimized {

    private final IInteractionReplyService replyService;

    //    @RabbitListener(
//            bindings = @QueueBinding(
//                    value = @Queue(name = "qa.liked.times.queue", durable = "true"),
//                    exchange = @Exchange(name = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
//                    key = MqConstants.Key.QA_LIKED_TIMES_KEY
//            )
//    )
    public void listenLikeTimesChange(LikedTimesDTO dto) {
        // * 健壮性检查
        if (dto == null) {
            log.error("LikedTimesDTO消息数据有误");
            return;
        }
        // * 转reply更新
        InteractionReply reply = new InteractionReply();
        reply.setId(dto.getBizId());
        reply.setLikedTimes(dto.getLikedTimes());
        replyService.updateById(reply);
    }
}
