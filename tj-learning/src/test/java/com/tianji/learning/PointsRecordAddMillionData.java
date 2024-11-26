package com.tianji.learning;

import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.service.IPointsRecordService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

/**
 * @author CamelliaV
 * @since 2024/11/23 / 0:36
 */
@SpringBootTest(classes = LearningApplication.class)
@Slf4j
public class PointsRecordAddMillionData {
	// * 必须Autowired
	@Autowired
	private IPointsRecordService pointsRecordService;

	@Test
	public void addMillionPointsRecord() {
		// * 表应该为int积分类型
		for (int i = 0; i < 1_000_000; i += 1000) {
			List<PointsRecord> recordList = new ArrayList<>();
			for (int j = 0; j < 1000; j++) {
				long userId = (int) (Math.random() * 10001);
				int type = (int) (Math.random() * 5) + 1;
				PointsRecordType recordType = PointsRecordType.of(type);
				int points = (int) (Math.random() * 1145140721) + 1;
				PointsRecord record = new PointsRecord();
				record.setPoints(points);
				record.setType(recordType);
				record.setUserId(userId);
				recordList.add(record);
			}
			pointsRecordService.saveBatch(recordList);
		}
	}
}
