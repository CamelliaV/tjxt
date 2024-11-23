package com.tianji.learning.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.tianji.learning.utils.TableInfoContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

/**
 * @author CamelliaV
 * @since 2024/11/21 / 19:18
 */
@Configuration
public class MybatisPlusConfiguration {
    @Bean
    public DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor() {
        // * key为需要替换的表明，value为对应的handler
        HashMap<String, TableNameHandler> tableMap = new HashMap<>();
        tableMap.put("points_board", new TableNameHandler() {
            @Override
            public String dynamicTableName(String sql, String tableName) {
                return TableInfoContext.getInfo();
            }
        });
        return new DynamicTableNameInnerInterceptor(tableMap);
    }
}
