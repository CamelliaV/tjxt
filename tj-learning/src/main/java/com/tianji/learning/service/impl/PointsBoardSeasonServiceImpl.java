package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-18
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {
    /**
     * 赛季列表查询
     */
    @Override
    public List<PointsBoardSeasonVO> queryPointsBoardSeasons() {
        LocalDate now = LocalDate.now();
        // * 切为本月第一天
        LocalDate monthBegin = DateUtils.getMonthBegin(now);
        // * 查询所有第一天小于本月第一天的，即所有过往赛季
        List<PointsBoardSeason> records = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, monthBegin)
                .list();
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptyList();
        }
        // * 封装返回
        return BeanUtils.copyToList(records, PointsBoardSeasonVO.class);
    }
}
