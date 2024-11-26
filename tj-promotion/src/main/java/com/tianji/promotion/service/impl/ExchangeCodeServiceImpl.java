package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.CodeVO;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.utils.CodeUtil;
import io.lettuce.core.RedisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-26
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {
	private final StringRedisTemplate redisTemplate;

	/**
	 * 生成兑换码
	 */
	@Override
	// @Async("generateExchangeCodeExecutor")
	public void generateCode(Coupon coupon) {
		log.info(">>>>> generateCode by {}", Thread.currentThread().getId());
		// * 获得优惠劵数量（兑换码生成数量）
		Integer totalNum = coupon.getTotalNum();
		// * 遍历生成，批量保存
		Long result = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
		if (result == null) {
			throw new RedisException("Redis兑换码自增id获取失败");
		}
		// * 传入序列号与优惠劵id
		long couponId = coupon.getId().longValue();
		long maxId = result.longValue();
		List<ExchangeCode> exchangeCodeList = new ArrayList<>();
		for (long i = maxId - totalNum + 1; i <= maxId; i++) {
			String code = CodeUtil.generateCode(i, couponId);
			// * 约定为32位，可以强转
			ExchangeCode exchangeCode = new ExchangeCode();
			exchangeCode.setCode(code);
			exchangeCode.setId(i);
			exchangeCode.setExchangeTargetId(couponId);
			exchangeCode.setExpiredTime(coupon.getIssueEndTime());
			exchangeCodeList.add(exchangeCode);
		}
		// * 存入数据库
		saveBatch(exchangeCodeList);
	}

	/**
	 * 分页查询兑换码
	 */
	@Override
	public PageDTO<CodeVO> queryCodePage(CodeQuery query) {
		Page<ExchangeCode> page = lambdaQuery()
				.eq(ExchangeCode::getExchangeTargetId, query.getCouponId())
				.eq(ExchangeCode::getStatus, query.getStatus())
				.page(query.toMpPageDefaultSortByCreateTimeDesc());
		List<ExchangeCode> records = page.getRecords();
		if (CollUtils.isEmpty(records)) {
			return PageDTO.empty(page);
		}
		List<CodeVO> codeVOList = BeanUtils.copyList(records, CodeVO.class);
		return PageDTO.of(page, codeVOList);
	}
}
