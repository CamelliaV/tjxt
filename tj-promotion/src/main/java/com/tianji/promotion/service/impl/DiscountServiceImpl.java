package com.tianji.promotion.service.impl;

import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCouponDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.util.PermuteUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author CamelliaV
 * @since 2024/12/1 / 20:20
 */
@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements IDiscountService {
	private final UserCouponMapper userCouponMapper;
	private final ICouponScopeService couponScopeService;
	private final Executor discountSolutionExecutor;

	@Override
	public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourseDTOList) {
		// * 查询用户可用优惠卷
		Long userId = UserContext.getUser();
		List<Coupon> couponList = userCouponMapper.queryUserAvailableCoupon(userId);
		if (CollUtils.isEmpty(couponList)) {
			return CollUtils.emptyList();
		}
		// * 筛选订单总价达到门槛的优惠劵
		int totalPrice = orderCourseDTOList.stream()
				.mapToInt(OrderCourseDTO::getPrice)
				.sum();
		List<Coupon> validCouponList = couponList.stream()
				.filter(c -> DiscountStrategy.getDiscount(c.getDiscountType()).canUse(totalPrice, c))
				.collect(Collectors.toList());
		if (CollUtils.isEmpty(couponList)) {
			return CollUtils.emptyList();
		}
		// * 获得优惠劵可用范围
		Map<Coupon, List<OrderCourseDTO>> validCouponMap = findValidCouponMap(validCouponList, orderCourseDTOList);
		if (CollUtils.isEmpty(validCouponMap)) {
			return CollUtils.emptyList();
		}
		// * 全排列
		validCouponList = new ArrayList<>(validCouponMap.keySet());
		List<List<Coupon>> solutions = PermuteUtil.permute(validCouponList);
		// * 加单卷
		for (Coupon coupon : validCouponList) {
			solutions.add(List.of(coupon));
		}
		// * 去重，防止本来就只有单卷
		solutions = new ArrayList<>(new HashSet<>(solutions));
		// * 遍历方案集合计算组合方案，封装dto，多线程操作
		List<CouponDiscountDTO> couponDiscountDTOList = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch latch = new CountDownLatch(solutions.size());
		for (List<Coupon> solution : solutions) {
			CompletableFuture.supplyAsync(new Supplier<CouponDiscountDTO>() {
				@Override
				public CouponDiscountDTO get() {
					return calculateSolution(solution, validCouponMap, orderCourseDTOList);
				}
			}, discountSolutionExecutor).thenAccept(
					dto -> {
						couponDiscountDTOList.add(dto);
						latch.countDown();
					}
			);
		}

		try {
			latch.await(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException("计算方案超时");
		}
		// * 筛选最优解
		return findBestSolution(couponDiscountDTOList);
	}

	@Override
	public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO dto) {
		// * 查询用户卷对应的优惠劵
		List<Coupon> validCouponList = userCouponMapper.queryCouponByUserCouponIds(dto.getUserCouponIds(), UserCouponStatus.UNUSED);
		if (CollUtils.isEmpty(validCouponList)) {
			return null;
		}
		// * 查询优惠劵可用课程
		List<OrderCourseDTO> orderCourseDTOList = dto.getCourseList();
		if (CollUtils.isEmpty(orderCourseDTOList)) {
			return null;
		}
		Map<Coupon, List<OrderCourseDTO>> validCouponMap = findValidCouponMap(validCouponList, orderCourseDTOList);
		if (CollUtils.isEmpty(validCouponMap)) {
			return null;
		}
		// * 计算优惠详情 （复用原本接口，修改一条设置detailMap）
		return calculateSolution(validCouponList, validCouponMap, orderCourseDTOList);
	}

	/**
	 * 根据可用优惠劵构建所有方案计算优惠并选出最优方案
	 */
	private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> couponDiscountDTOList) {
		// * （用卷相同）优惠金额最高/（优惠金额相同）用卷最少
		Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
		Map<String, CouponDiscountDTO> lessCouponMap = new HashMap<>();
		// * 遍历方案
		for (CouponDiscountDTO solution : couponDiscountDTOList) {
			// * 用卷id组合
			String ids = solution.getIds()
					.stream()
					.sorted(Long::compareTo)
					.map(String::valueOf)
					.collect(Collectors.joining(","));
			// * 用卷相同时，优惠金额最高
			CouponDiscountDTO best = moreDiscountMap.get(ids);
			// * 可访问时，折扣价格不高于最好，不做更新
			if (best != null && best.getDiscountAmount() >= solution.getDiscountAmount()) {
				continue;
			}
			// * 可访问时，用卷不少于最好，不做更新
			best = lessCouponMap.get(String.valueOf(solution.getDiscountAmount()));
			// * 保留单卷
			if (best != null && best.getIds().size() <= solution.getIds().size() && solution.getIds().size() > 1) {
				continue;
			}
			// * 更新
			moreDiscountMap.put(ids, solution);
			lessCouponMap.put(String.valueOf(solution.getDiscountAmount()), solution);
		}
		// * 求交集并按折扣金额降序
		Collection<CouponDiscountDTO> intersection = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());
		return intersection.stream().sorted(Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount).reversed()).collect(Collectors.toList());
	}

	/**
	 * 根据指定方案计算优惠
	 */

	// * 计算优惠明细
	private CouponDiscountDTO calculateSolution(List<Coupon> solution, Map<Coupon, List<OrderCourseDTO>> validCouponMap, List<OrderCourseDTO> orderCourseDTOList) {
		// * 初始化返回dto与折扣明细Map
		CouponDiscountDTO dto = new CouponDiscountDTO();
		Map<Long, Integer> detailMap = orderCourseDTOList.stream()
				.collect(Collectors.toMap(OrderCourseDTO::getId, c -> 0));
		// * 遍历每张优惠劵计算优惠明细
		for (Coupon coupon : solution) {
			// * 获得优惠卷限定课程
			List<OrderCourseDTO> validCourseList = validCouponMap.get(coupon);
			// * 获得课程总价（原价-折扣明细）
			int actualTotalPrice = validCourseList.stream()
					.mapToInt(c -> c.getPrice() - detailMap.get(c.getId()))
					.sum();
			// * 判断是否可用
			Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
			boolean canUse = discount.canUse(actualTotalPrice, coupon);
			if (!canUse) {
				continue;
			}
			// * 计算优惠金额
			int discountAmount = discount.calculateDiscount(actualTotalPrice, coupon);
			// * 计算优惠明细
			calculateDiscountDetail(detailMap, actualTotalPrice, discountAmount, validCourseList);
			// * 填充dto（字段默认值，均不为null）
			dto.getIds().add(coupon.getCreater());
			dto.getRules().add(discount.getRule(coupon));
			dto.setDiscountAmount(dto.getDiscountAmount() + discountAmount);
		}
		dto.setDiscountDetailMap(detailMap);
		return dto;
	}

	// * 计算优惠明细
	private void calculateDiscountDetail(Map<Long, Integer> detailMap, int actualTotalPrice, int discountAmount, List<OrderCourseDTO> validCourseList) {
		// * 课程价格占总价比例 * 优惠总值（最后一个用减法）
		int times = 0;
		int remain = discountAmount;
		for (OrderCourseDTO course : validCourseList) {
			++times;
			int discount = 0;
			if (times == validCourseList.size()) {
				// * 最后一门
				discount = remain;
			} else {
				discount = course.getPrice() * discountAmount / actualTotalPrice;
				remain -= discount;
			}
			detailMap.put(course.getId(), discount);
		}
	}

	private Map<Coupon, List<OrderCourseDTO>> findValidCouponMap(List<Coupon> validCouponList, List<OrderCourseDTO> orderCourseDTOList) {
		Map<Coupon, List<OrderCourseDTO>> validCouponMap = new HashMap<>();
		// * 遍历优惠劵列表，对限定了范围的优惠劵，查询限定范围信息
		for (Coupon coupon : validCouponList) {
			List<OrderCourseDTO> validCourseList = orderCourseDTOList;
			// * 限定范围，需要对可用课程列表进行过滤
			if (BooleanUtils.isTrue(coupon.getSpecific())) {
				// * 查询得到限定范围对应信息
				List<CouponScope> scopeList = couponScopeService.lambdaQuery()
						.eq(CouponScope::getId, coupon.getId())
						.list();
				// * 映射限定分类id
				Set<Long> scopeIds = scopeList.stream()
						.map(CouponScope::getBizId)
						.collect(Collectors.toSet());
				// * 限定范围是否囊括了订单里的课程
				validCourseList = validCourseList.stream()
						.filter(c -> scopeIds.contains(c.getCateId()))
						.collect(Collectors.toList());
			}
			// * 订单里没有可用优惠劵的限定课程
			if (CollUtils.isEmpty(validCourseList)) {
				continue;
			}
			// * 对可用的课程计算总价
			int totalPrice = validCourseList.stream().mapToInt(OrderCourseDTO::getPrice).sum();
			// * 判断是否达到优惠门槛
			boolean canUse = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalPrice, coupon);
			if (canUse) {
				validCouponMap.put(coupon, validCourseList);
			}
		}
		return validCouponMap;
	}
}
