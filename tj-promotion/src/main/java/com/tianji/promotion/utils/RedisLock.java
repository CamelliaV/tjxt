package com.tianji.promotion.utils;

import com.tianji.common.utils.BooleanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author CamelliaV
 * @since 2024/11/27 / 17:06
 */
@RequiredArgsConstructor
@Component
public class RedisLock {
	private final StringRedisTemplate redisTemplate;

	public boolean tryLock(String key, long releaseTime, TimeUnit unit) {
		Boolean result = redisTemplate.opsForValue().setIfAbsent(key, Thread.currentThread().getName(), releaseTime, unit);
		return BooleanUtils.isTrue(result);
	}

	public void unlock(String key) {
		redisTemplate.delete(key);
	}
}
