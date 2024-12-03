package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.exceptions.BizIllegalException;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.List;

/**
 * @author CamelliaV
 * @since 2024/12/2 / 14:32
 */
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {
	@Override
	public PromotionClient create(Throwable cause) {
		return new PromotionClient() {
			@Override
			public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourseDTOList) {
				return List.of();
			}

			@Override
			public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
				return null;
			}

			@Override
			public void writeOffCoupon(List<Long> userCouponIds) {
				throw new BizIllegalException(500, "核销优惠券异常", cause);
			}

			@Override
			public void refundCoupon(List<Long> userCouponIds) {
				throw new BizIllegalException(500, "退还优惠券异常", cause);
			}

			@Override
			public List<String> queryDiscountRules(List<Long> userCouponIds) {
				return List.of();
			}
		};
	}
}
