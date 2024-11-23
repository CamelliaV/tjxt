package com.tianji.learning;

import com.tianji.common.utils.DateUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * @author CamelliaV
 * @since 2024/11/22 / 12:38
 */
@SpringBootTest(classes = LearningApplication.class)
@Slf4j
public class PointsBoardSeasonAddTillNow {
    @Autowired
    private IPointsBoardSeasonService seasonService;

    @Test
    public void addAllSeasonTillNow() {
        List<PointsBoardSeason> seasons = seasonService.list();
        if (seasons == null) {
            log.info("数据库没有赛季数据");
            return;
        }
        PointsBoardSeason lastSeason = seasons.get(seasons.size() - 1);
        LocalDate begin = lastSeason.getBeginTime();
        Integer seasonId = lastSeason.getId();
        LocalDate target = LocalDate.of(2030, 9, 10);
        LocalDate targetBegin = DateUtils.getMonthBegin(target);
        List<PointsBoardSeason> newSeasonList = new ArrayList<>();
        while (begin.isBefore(targetBegin)) {
            PointsBoardSeason newSeason = new PointsBoardSeason();
            begin = begin.plusMonths(1);
            LocalDate end = begin.plusDays(begin.lengthOfMonth() - 1);
            newSeason.setName("第" + (++seasonId) + "赛季");
            newSeason.setBeginTime(begin);
            newSeason.setEndTime(end);
            newSeasonList.add(newSeason);
        }
        seasonService.saveBatch(newSeasonList);
    }
}
