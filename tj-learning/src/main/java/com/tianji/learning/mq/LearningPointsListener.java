package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.PointsMessage;
import com.tianji.learning.service.IPointsRecordService;
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
 * @since 2024/11/18 / 23:05
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LearningPointsListener {
    private final IPointsRecordService pointsRecordService;

    @RabbitListener(
            bindings = @QueueBinding(
                    exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
                    value = @Queue(value = MqConstants.Queue.SIGN_POINTS_QUEUE, durable = "true"),
                    key = MqConstants.Key.SIGN_IN

            )

    )
    public void listenSignMessage(PointsMessage message) {
        if (message == null) {
            log.error("Sign:PointsMessage为空");
            return;
        }

        pointsRecordService.addPointsRecord(message, PointsRecordType.SIGN);
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
                    value = @Queue(value = MqConstants.Queue.LEARNING_POINTS_QUEUE, durable = "true"),
                    key = MqConstants.Key.LEARN_SECTION

            )

    )
    public void listenLearningMessage(PointsMessage message) {
        if (message == null) {
            log.error("Learning:PointsMessage为空");
            return;
        }

        pointsRecordService.addPointsRecord(message, PointsRecordType.LEARNING);
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
                    value = @Queue(value = MqConstants.Queue.QA_POINTS_QUEUE, durable = "true"),
                    key = MqConstants.Key.WRITE_REPLY

            )

    )
    public void listenQAMessage(PointsMessage message) {
        if (message == null) {
            log.error("QA:PointsMessage为空");
            return;
        }

        pointsRecordService.addPointsRecord(message, PointsRecordType.QA);
    }
}
