package com.tianji.remark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.remark.domain.po.LikedRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 点赞记录表 Mapper 接口
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-13
 */
public interface LikedRecordMapper extends BaseMapper<LikedRecord> {
    @Delete("<script>" +
            "DELETE FROM tj_remark.liked_record WHERE (biz_id, user_id, biz_type) IN " +
            "<foreach collection='records' item='record' open='(' separator=',' close=')'>" +
            "(#{record.bizId}, #{record.userId}, #{record.bizType})" +
            "</foreach>" +
            "</script>")
    int batchDeleteByUniqueKey(@Param("records") List<LikedRecord> recordsToUpdate);
}
