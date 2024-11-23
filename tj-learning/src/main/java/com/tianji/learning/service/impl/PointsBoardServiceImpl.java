package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-21
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    /**
     * 根据赛季id查询用户数据与榜单数据
     */
    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        Long seasonId = query.getSeason();
        Long userId = UserContext.getUser();
        String userIdString = userId.toString();
        LocalDate now = LocalDate.now();
        PointsBoardVO vo = new PointsBoardVO();
        List<PointsBoard> boardList = new ArrayList<>();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // * 无赛季id传参或为0，查询当前赛季
        if (seasonId == null || seasonId == 0) {
            // * Redis查询当前用户积分与排名
            Long userRank = redisTemplate.opsForZSet()
                                         .reverseRank(key, userIdString);
            Double userPoints = redisTemplate.opsForZSet()
                                             .score(key, userIdString);
            vo.setRank(userRank != null ? userRank.intValue() + 1 : 0);
            vo.setPoints(userPoints != null ? userPoints.intValue() : 0);
            // * Redis查询当前赛季排行榜
            // * 校验过的分页字段
            int pageNo = query.getPageNo();
            int pageSize = query.getPageSize();
            int start = (pageNo - 1) * pageSize;
            int end = start + pageSize - 1;
            Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                                                                              .reverseRangeWithScores(key, start, end);
            // * Redis无排行数据
            if (typedTuples == null) {
                return vo;
            }
            int rank = start + 1;
            // * 暂存结果
            for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                String user = tuple.getValue();
                Double points = tuple.getScore();
                PointsBoard item = new PointsBoard();
                // * 数据残缺
                if (points == null || user == null) {
                    continue;
                }
                item.setRank(rank++);
                item.setPoints(points.intValue());
                item.setUserId(Long.valueOf(user));
                boardList.add(item);
            }
            // * 构造userIdList查询用户信息
            List<Long> userIdList = boardList.stream()
                                             .map(PointsBoard::getUserId)
                                             .collect(Collectors.toList());
            // * 查询用户名并填补itemvo
            List<PointsBoardItemVO> itemVOList = new ArrayList<>();
            if (CollUtils.isNotEmpty(userIdList)) {
                List<UserDTO> userDTOS = userClient.queryUserByIds(userIdList);
                if (CollUtils.isNotEmpty(userDTOS)) {
                    Map<Long, String> userMap = userDTOS.stream()
                                                        .collect(Collectors.toMap(UserDTO::getId, UserDTO::getUsername));
                    for (PointsBoard item : boardList) {
                        PointsBoardItemVO itemVO = new PointsBoardItemVO();
                        itemVO.setPoints(item.getPoints());
                        itemVO.setRank(item.getRank());
                        String userName = userMap.get(item.getUserId());
                        if (userName != null) {
                            itemVO.setName(userName);
                        }
                        itemVOList.add(itemVO);
                    }
                }
            }
            // * 补全vo
            vo.setBoardList(itemVOList);
        } else {
            // * 查询历史赛季
            // * 拼接历史赛季表名，放入ThreadLocal用于拦截器获取表名查询
            String tableName = LearningConstants.POINTS_BOARD_TABLE_PREFIX + seasonId;
            TableInfoContext.setInfo(tableName);
            // * 查询用户自己的数据
            PointsBoard userBoardItem = lambdaQuery()
                    .eq(PointsBoard::getUserId, userId)
                    .one();
            // * 设置自己排行与积分
            vo.setRank(userBoardItem != null ? userBoardItem.getId()
                                                            .intValue() : 0);
            vo.setPoints(userBoardItem != null ? userBoardItem.getPoints() : 0);
            // * 分页查询数据库
            Page<PointsBoard> boardPage = lambdaQuery()
                    .page(query.toMpPage(new OrderItem("id", true)));

            List<PointsBoardItemVO> itemVOList = new ArrayList<>();
            List<PointsBoard> records = boardPage.getRecords();
            // * 数据库记录不为空
            if (CollUtils.isNotEmpty(records)) {
                // * 构造userId列表查询用户名
                List<Long> userIdList = records.stream()
                                               .map(PointsBoard::getUserId)
                                               .collect(Collectors.toList());
                List<UserDTO> userDTOS = userClient.queryUserByIds(userIdList);
                Map<Long, String> userMap = new HashMap<>();
                // * 封装映射关系到map
                if (CollUtils.isNotEmpty(userDTOS)) {
                    userMap = userDTOS.stream()
                                      .collect(Collectors.toMap(UserDTO::getId, UserDTO::getUsername));
                }
                // * 组装voList
                for (PointsBoard record : records) {
                    PointsBoardItemVO itemVO = new PointsBoardItemVO();
                    itemVO.setRank(record.getId()
                                         .intValue());
                    itemVO.setPoints(record.getPoints());
                    String userName = userMap.get(record.getUserId());
                    if (userName != null) {
                        itemVO.setName(userName);
                    }
                    itemVOList.add(itemVO);
                }
            }
            // * 组装结果vo
            vo.setBoardList(itemVOList);
            // * 删去TL
            TableInfoContext.remove();
        }
        // * 返回vo
        return vo;
    }

    /**
     * 根据赛季表名创建积分排行榜历史表
     */
    @Override
    public void createPointsBoardTableBySeason(String tableName) {
        getBaseMapper().createPointsBoardTableBySeason(tableName);
    }
}
