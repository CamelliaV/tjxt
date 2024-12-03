package com.tianji.promotion.service;

import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCouponDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;

import java.util.List;

/**
 * @author CamelliaV
 * @since 2024/12/1 / 20:20
 */

public interface IDiscountService {
	List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourseDTOList);

	CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO dto);
}
