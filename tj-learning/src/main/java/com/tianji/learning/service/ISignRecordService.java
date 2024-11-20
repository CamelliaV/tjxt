package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;

/**
 * @author CamelliaV
 * @since 2024/11/18 / 15:47
 */
public interface ISignRecordService {
    SignResultVO addSignRecord();

    Byte[] querySignRecords();
}
