package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.message.PointsMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-18
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    /**
     * 添加积分记录
     */
    @Override
    public void addPointsRecord(PointsMessage message, PointsRecordType type) {
        // * 判断业务类型是否有积分上限
        int maxPoints = type.getMaxPoints();
        int points = message.getPoints();
        int pointsToAdd = points;
        Long userId = message.getUserId();

        if (maxPoints > 0) {
            // * 查询今日此类型业务已获积分
            LocalDateTime now = LocalDateTime.now();
            Integer todayPoints = getBaseMapper().queryUserTodayPoints(userId, type, DateUtils.getDayStartTime(now), DateUtils.getDayEndTime(now));
            // * 没有相关数据
            if (todayPoints == null) {
                todayPoints = 0;
            }
            // * 今日分值已达上限
            if (todayPoints >= maxPoints) {
                return;
            }
            // * 加完分不超过上限，加原始分数，否则加差值分数
            if (todayPoints + points > maxPoints) {
                pointsToAdd = maxPoints - todayPoints;
            }
        }

        // * 构造数据保存
        PointsRecord record = new PointsRecord();
        record.setPoints(pointsToAdd)
              .setType(type)
              .setUserId(userId);
        save(record);
    }


    /**
     * 查询今日积分情况
     */
    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        // * 构造mapper传参
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        Long userId = UserContext.getUser();
        // * 数据库查询对应不同类型获得分数，结果为列表（结果别名复用po）
        List<PointsRecord> records = getBaseMapper().queryMyPointsToday(userId, start, end);
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptyList();
        }
        // * 封装vo
        List<PointsStatisticsVO> voList = new ArrayList<>();
        for (PointsRecord record : records) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setPoints(record.getPoints());
            PointsRecordType type = record.getType();
            vo.setType(type.getDesc());
            vo.setMaxPoints(type.getMaxPoints());
            voList.add(vo);
        }

        return voList;
    }
}
