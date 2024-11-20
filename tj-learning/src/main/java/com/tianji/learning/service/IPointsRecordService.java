package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.PointsMessage;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-18
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(PointsMessage message, PointsRecordType type);

    List<PointsStatisticsVO> queryMyPointsToday();
}
