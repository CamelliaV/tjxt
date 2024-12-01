package com.tianji.promotion.task;

import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IUserCouponService;
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
 * @since 2024/11/27 / 22:05
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PromotionMqHandler {
	private final IUserCouponService userCouponService;
	private final ICouponService couponService;

	@RabbitListener(
			bindings = @QueueBinding(
					value = @Queue(value = "coupon.received.queue", durable = "true"),
					exchange = @Exchange(value = MqConstants.Exchange.PROMOTION_EXCHANGE, type = ExchangeTypes.TOPIC),
					key = MqConstants.Key.COUPON_RECEIVED
			)
	)
	public void listenCouponReceiveMessage(UserCouponDTO dto) {
		// * 无dto结束业务
		if (dto == null) {
			log.error("领劵消息数据为null");
			return;
		}
		// * 查优惠劵信息用于更新状态
		Coupon coupon = couponService.getById(dto.getCouponId());
		if (coupon == null) {
			throw new BizIllegalException("目标优惠卷不存在：" + dto.getCouponId());
		}
		// * 调用更新
		userCouponService.checkAndCreateUserCoupon(coupon, dto.getUserId());
	}

	@RabbitListener(
			bindings = @QueueBinding(
					value = @Queue(value = "coupon.exchange.queue", durable = "true"),
					exchange = @Exchange(value = MqConstants.Exchange.PROMOTION_EXCHANGE, type = ExchangeTypes.TOPIC),
					key = MqConstants.Key.COUPON_EXCHANGED
			)
	)
	public void listenCouponExchangeMessage(UserCouponDTO dto) {
		// * 无dto结束业务
		if (dto == null) {
			log.error("兑换消息数据为null");
			return;
		}
		// * 查优惠劵信息用于更新状态（需要总数量作乐观锁，需要查询数据库）
		Coupon coupon = couponService.getById(dto.getCouponId());
		if (coupon == null) {
			throw new BizIllegalException("目标优惠卷不存在：" + dto.getCouponId());
		}
		// * 调用更新
		userCouponService.checkAndCreateUserCouponWithCode(coupon, dto.getUserId(), dto.getSerialNum());
	}
}
