package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.message.PointsMessage;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.task.PointsRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

	private static final int POINTS_RECORD_SHARDING_RESULT_NUM = 2;
	private static final int MAX_ID_INDEX = 0;
	private static final int MIN_ID_INDEX = 1;
	private final StringRedisTemplate redisTemplate;
	private final IPointsBoardSeasonService seasonService;
	@Autowired
	private PointsRecordDelayTaskHandler delayTaskHandler;

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
		LocalDateTime now = LocalDateTime.now();

		if (maxPoints > 0) {
			// * 查询今日此类型业务已获积分
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

		// * 累加分数到zset 前缀+年月作key 用户id member 分score
		String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
		redisTemplate.opsForZSet()
				.incrementScore(key, userId.toString(), pointsToAdd);
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

	/**
	 * 分表存档积分记录数据简易版（冷启动下测试百万数据总和不超过1s）
	 * tj_learning> ALTER TABLE points_record RENAME points_record_xx
	 * [2024-11-23 13:42:09] completed in 601 ms
	 * tj_learning> CREATE TABLE points_record LIKE points_record_xx
	 * [2024-11-23 13:42:09] completed in 357 ms
	 */
	public void pointsRecordArchiveSimple() {
		// * 上一赛季时间
		LocalDate time = LocalDate.now()
				.minusMonths(1);
		PointsBoardSeason season = seasonService.lambdaQuery()
				.ge(PointsBoardSeason::getEndTime, time)
				.le(PointsBoardSeason::getBeginTime, time)
				.one();
		if (season == null) {
			return;
		}
		String seasonId = String.valueOf(season.getId());
		// * 重命名原表为分片表，再用LIKE创建新表（采用原表名）
		getBaseMapper().renamePointsRecordTableToSharding(seasonId);
		getBaseMapper().copyPointsRecordShardingTableDefinition(seasonId);
	}

	/**
	 * 分表存档积分记录数据（实现，不采用）
	 */
	@Override
	@Transactional
	public void pointsRecordArchive() {
		LocalDate time = LocalDate.now()
				.minusMonths(1);
		PointsBoardSeason season = seasonService.lambdaQuery()
				.ge(PointsBoardSeason::getEndTime, time)
				.le(PointsBoardSeason::getBeginTime, time)
				.one();
		if (season == null) {
			return;
		}
		// * 传入分表key赛季id
		String seasonId = String.valueOf(season.getId());
		// * 创建分表
		getBaseMapper().createPointsRecordShardingTable(seasonId);
		// * 复制数据插入，只要读锁
		Integer inserted = getBaseMapper().insertAllToShardingTable(seasonId);
		if (inserted == null) {
			throw new DbException("复制插入points_record记录分表失败");
		}
		// * 查询删除id范围
		List<Long> results = getBaseMapper().queryMaxMinId();

		if (CollUtils.isEmpty(results) || results.size() != POINTS_RECORD_SHARDING_RESULT_NUM) {
			throw new DbException("数据库points_record数据获取id范围失败");
		}
		// * 获取待删除id范围
		Long max_id = results.get(MAX_ID_INDEX);
		Long min_id = results.get(MIN_ID_INDEX);
		if (max_id == null || min_id == null) {
			throw new DbException("数据库points_record分表操作待删除范围获取失败");
		}
		// * 添加所有任务到延时队列
		// * 每给定一段时间（常量定义）范围获取任务结果删除（指定不变id范围内固定limit删除）
		for (long id = min_id; id <= max_id; id += LearningConstants.SHARDING_POINTS_RECORD_DELETE_LIMIT) {
			delayTaskHandler.addPointsRecordShardingTask(min_id, max_id);
		}
	}

	/**
	 * 范围删除积分记录（延时任务用）
	 */
	@Override
	public void deletePointsRecordWithRange(Long minId, Long maxId, int limit) {
		getBaseMapper().deletePointsRecordWithRange(minId, maxId, limit);
	}
}
