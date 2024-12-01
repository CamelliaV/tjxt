package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-26
 */
@RestController
@RequestMapping("/user-coupons")
@Api(tags = "用户卷相关接口")
@RequiredArgsConstructor
public class UserCouponController {
	private final IUserCouponService userCouponService;

	@ApiOperation("领取优惠劵")
	@PostMapping("/{id}/receive")
	public void receiveCoupon(@PathVariable("id") Long id) {
		// userCouponService.receiveCoupon(id);
		userCouponService.receiveCouponImplWithLua(id);
	}

	@ApiOperation("兑换优惠劵")
	@PostMapping("/{code}/exchange")
	public void exchangeCoupon(@PathVariable("code") String code) {
		// userCouponService.exchangeCoupon(code);
		userCouponService.exchangeCouponWithLua(code);
	}

	@ApiOperation("分页查询我的优惠劵")
	@GetMapping("/page")
	public PageDTO<CouponVO> queryMyCoupon(UserCouponQuery query) {
		return userCouponService.queryMyCoupon(query);
	}
}
