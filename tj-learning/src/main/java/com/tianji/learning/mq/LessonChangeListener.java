package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @author CamelliaV
 * @since 2024/10/30 / 21:48
 */

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService lessonService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = MqConstants.Queue.ORDER_PAY_QUEUE, durable = "true"),
                    exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
                    key = MqConstants.Key.ORDER_PAY_KEY
            )
    )
    public void listenLessonPay(OrderBasicDTO dto) {
        // * 健壮性检查
        if (dto == null || dto.getUserId() == null || CollUtils.isEmpty(dto.getCourseIds())) {
            log.error("MQ消息错误，订单数据为空");
            return;
        }

        // * 为对应用户添加课程(信息均存放于dto中)
        lessonService.addLesson(dto);
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = MqConstants.Queue.ORDER_REFUND_QUEUE, durable = "true"),
                    exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
                    key = MqConstants.Key.ORDER_REFUND_KEY
            )
    )
    public void listenLessonRefund(OrderBasicDTO dto) {
        // * 健壮性检查
        if (dto == null || dto.getUserId() == null || CollUtils.isEmpty(dto.getCourseIds())) {
            log.error("MQ消息错误，退款数据为空");
            return;
        }

        // * 为对应用户删除课程
        Long userId = dto.getUserId();
        if (userId == null) {
            return;
        }
        Long courseId = dto.getCourseIds()
                           .get(0);
        if (courseId == null) {
            return;
        }
        lessonService.deleteLessonByCourse(userId, courseId);
    }

}
