package com.tianji.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.enums.CouponStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-25
 */
public interface CouponMapper extends BaseMapper<Coupon> {
	// * 不确定#{}表达式能否正确求值，并且没有自动导入
	@Update("UPDATE tj_promotion.coupon SET status = #{newStatus} WHERE id MOD #{shardTotal} = #{shardIndex} AND " +
			"issue_begin_time <= " +
			"#{now} AND coupon.status = #{oldStatus} LIMIT #{size}")
	Integer updateCouponIssueStatusByPage(@Param("shardIndex") int shardIndex, @Param("shardTotal") int shardTotal,
	                                      @Param("size") int size, @Param("oldStatus") CouponStatus oldStatus,
	                                      @Param("now") LocalDateTime now, @Param("newStatus") CouponStatus newStatus);

	@Update("UPDATE tj_promotion.coupon SET status = #{newStatus} WHERE id MOD #{shardTotal} = #{shardIndex} AND " +
			"issue_end_time <= " +
			"#{now} AND coupon.status = #{oldStatus} LIMIT #{size}")
	Integer updateCouponFinishStatusByPage(@Param("shardIndex") int shardIndex, @Param("shardTotal") int shardTotal,
	                                       @Param("size") int size, @Param("oldStatus") CouponStatus oldStatus,
	                                       @Param("now") LocalDateTime now, @Param("newStatus") CouponStatus newStatus);
}
