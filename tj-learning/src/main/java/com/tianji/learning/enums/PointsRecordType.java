package com.tianji.learning.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.tianji.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum PointsRecordType implements BaseEnum {
    LEARNING(1, "课程学习", 50, 10),
    SIGN(2, "每日签到", 0, 1),
    QA(3, "课程问答", 20, 5),
    NOTE(4, "课程笔记", 20, 3),
    COMMENT(5, "课程评价", 0, 10),
    ;
    @EnumValue
    @JsonValue
    int value;
    String desc;
    int maxPoints;
    int rewardPoints;

    PointsRecordType(int value, String desc, int maxPoints) {
        this.value = value;
        this.desc = desc;
        this.maxPoints = maxPoints;
        this.rewardPoints = 0;
    }

    PointsRecordType(int value, String desc, int maxPoints, int rewardPoints) {
        this.value = value;
        this.desc = desc;
        this.maxPoints = maxPoints;
        this.rewardPoints = rewardPoints;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PointsRecordType of(Integer value) {
        if (value == null) {
            return null;
        }
        for (PointsRecordType status : values()) {
            if (status.equalsValue(value)) {
                return status;
            }
        }
        return null;
    }
}