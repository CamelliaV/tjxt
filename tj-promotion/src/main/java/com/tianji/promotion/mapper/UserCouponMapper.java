package com.tianji.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-26
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
	@Select("SELECT c.id, c.discount_type, c.`specific`, c.discount_value, c.threshold_amount, c.max_discount_amount," +
			" uc.id AS creater" +
			" FROM coupon c, " +
			"user_coupon uc WHERE c" +
			".id = " +
			"uc.coupon_id " +
			"AND uc.user_id" +
			" = #{userId} AND " +
			"uc" +
			".status " +
			"= 1")
	List<Coupon> queryUserAvailableCoupon(@Param("userId") Long userId);
}
