/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.datasource.impl.base;

import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;
import com.alibaba.nacos.plugin.datasource.manager.DatabaseDialectManager;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoGrayMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.Collections;

/**
 * The base implementation of ConfigInfoGrayMapper for Nacos 3.1.1+.
 * Compatible with extended database dialects (PostgreSQL, Oracle, etc.).
 *
 * @author nacos-plugin-ext
 */
public abstract class BaseConfigInfoGrayMapper extends AbstractMapper implements ConfigInfoGrayMapper {

    @Override
    public MapperResult findAllConfigInfoGrayForDumpAllFetchRows(MapperContext context) {
        DatabaseDialect dialect = DatabaseDialectManager.getInstance().getDialect(getDataSource());
        String innerSql = "SELECT id,data_id,group_id,tenant_id,gray_name,gray_rule,app_name,content,md5,gmt_modified FROM config_info_gray ORDER BY id";
        String sql = dialect.getLimitPageSqlWithOffset(innerSql, context.getStartRow(), context.getPageSize());
        return new MapperResult(sql, Collections.emptyList());
    }

    @Override
    public String getFunction(String functionName) {
        return DatabaseDialectManager.getInstance().getDialect(getDataSource()).getFunction(functionName);
    }
}
