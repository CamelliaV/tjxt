package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.PointsMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author CamelliaV
 * @since 2024/11/18 / 15:48
 */
@Service
@RequiredArgsConstructor
public class ISignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    /**
     * 签到
     */
    @Override
    public SignResultVO addSignRecord() {
        // * 使用bitmap实现签到，拼接用户id与年月作key，日计算offset，存入bitmap
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int offset = now.getDayOfMonth() - 1;
        // * bitmap属于字符串
        // * setBit返回之前的bit值
        Boolean result = redisTemplate.opsForValue()
                                      .setBit(key, offset, true);
        if (BooleanUtils.isTrue(result)) {
            throw new BizIllegalException("不能重复签到");
        }
        // * 统计连续签到天数
        // * 获取到当天的本月签到详情
        // * BITFIELD key GET u[dayOfMonth] 0
        int signDays = 0;
        List<Long> results = redisTemplate.opsForValue()
                                          .bitField(key, BitFieldSubCommands.create()
                                                                            .get(BitFieldSubCommands.BitFieldType.unsigned(offset + 1))
                                                                            .valueAt(0));
        // * 返回为10进制数据，转二进制&处理
        // * 计算连续签到天数
        if (CollUtils.isNotEmpty(results)) {
            int num = results.get(0)
                             .intValue();
            while ((num & 1) == 1) {
                signDays++;
                num >>>= 1;
            }
        }
        // * 封装vo
        // * 填充连续签到天数与奖励积分
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        int rewardPoints = 0;
        if (signDays == 7) {
            rewardPoints = 10;
        } else if (signDays == 14) {
            rewardPoints = 20;
        } else if (signDays == 28) {
            rewardPoints = 40;
        }
        vo.setRewardPoints(rewardPoints);

        // * 积分推送至mq
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE, MqConstants.Key.SIGN_IN, PointsMessage.of(userId, vo.totalPoints()));
        return vo;
    }

    /**
     * 查询签到记录
     */
    @Override
    public Byte[] querySignRecords() {
        // * bitField查询签到详情
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<Long> results = redisTemplate.opsForValue()
                                          .bitField(key, BitFieldSubCommands.create()
                                                                            .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                                                            .valueAt(0));
        if (CollUtils.isEmpty(results)) {
            return new Byte[0];
        }

        int num = results.get(0)
                         .intValue();
        // * 从最末尾（日期最大）开始填充
        Byte[] bytes = new Byte[dayOfMonth];
        int pos = dayOfMonth;
        while (--pos >= 0) {
            bytes[pos] = (byte) (num & 1);
            num >>>= 1;
        }

        return bytes;
    }
}
