package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-25
 */
@RestController
@RequestMapping("/coupons")
@Api(tags = "优惠劵相关接口")
@RequiredArgsConstructor
public class CouponController {
	private final ICouponService couponService;

	@ApiOperation("新增优惠劵")
	@PostMapping
	public void addCoupon(@RequestBody @Validated CouponFormDTO dto) {
		couponService.addCoupon(dto);
	}

	@ApiOperation("分页查询优惠劵")
	@GetMapping("/page")
	public PageDTO<CouponPageVO> queryPage(CouponQuery query) {
		return couponService.queryPage(query);
	}

	@ApiOperation("发放优惠劵")
	@PutMapping("/{id}/issue")
	public void issueCoupon(@PathVariable("id") Long id, @Validated @RequestBody CouponIssueFormDTO dto) {
		dto.setId(id);
		couponService.issueCoupon(dto);
	}


	@ApiOperation("修改优惠劵")
	@PutMapping("/{id}")
	public void updateCoupon(@PathVariable("id") Long id, @Validated @RequestBody CouponFormDTO dto) {
		dto.setId(id);
		couponService.updateCoupon(dto);
	}

	@ApiOperation("删除优惠劵")
	@DeleteMapping("/{id}")
	public void deleteCoupon(@PathVariable("id") Long id) {
		couponService.deleteCoupon(id);
	}

	@ApiOperation("根据id查询优惠劵")
	@GetMapping("/{id}")
	public CouponDetailVO queryCouponDetailById(@PathVariable("id") Long id) {
		return couponService.queryCouponDetailById(id);
	}

	@ApiOperation("暂停发放优惠劵")
	@PutMapping("/{id}/pause")
	public void pauseCouponIssue(@PathVariable("id") Long id) {
		couponService.pauseCouponIssue(id);
	}

}
