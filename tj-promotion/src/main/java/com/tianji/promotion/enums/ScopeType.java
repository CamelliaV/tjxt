package com.tianji.promotion.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.tianji.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ScopeType implements BaseEnum {
	ALL(0, "全部"),
	CATEGORY(1, "指定分类"),
	COURSE(2, "指定课程"),
	;
	public static final String CATEGORY_HANDLER_NAME = "CATEGORY";
	public static final String COURSE_HANDLER_NAME = "COURSE";
	private final int value;
	private final String desc;

	@JsonCreator
	public static ScopeType of(Integer value) {
		if (value == null) {
			return null;
		}
		for (ScopeType status : values()) {
			if (status.value == value) {
				return status;
			}
		}
		return null;
	}

	public static String desc(Integer value) {
		ScopeType status = of(value);
		return status == null ? "" : status.desc;
	}
}
