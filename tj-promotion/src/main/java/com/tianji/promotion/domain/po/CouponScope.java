package com.tianji.promotion.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tianji.promotion.enums.ScopeType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 优惠券作用范围信息
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("coupon_scope")
@NoArgsConstructor
public class CouponScope implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	/**
	 * 范围限定类型：1-分类，2-课程，等等
	 */
	private ScopeType type;
	/**
	 * 优惠券id
	 */
	private Long couponId;

	/**
	 * 优惠券作用范围的业务id，例如分类id、课程id
	 */
	private Long bizId;

	public CouponScope(ScopeType type, Long couponId, Long bizId) {
		this.type = type;
		this.couponId = couponId;
		this.bizId = bizId;
	}
}
