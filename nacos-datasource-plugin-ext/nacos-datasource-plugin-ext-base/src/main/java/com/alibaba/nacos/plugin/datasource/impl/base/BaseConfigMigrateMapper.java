/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.plugin.datasource.manager.DatabaseDialectManager;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigMigrateMapper;

/**
 * The base implementation of ConfigMigrateMapper for Nacos 3.1.1+.
 * All SQL is provided by default methods in ConfigMigrateMapper;
 * subclasses only need to implement getDataSource() and delegate getFunction to dialect.
 *
 * @author nacos-plugin-ext
 */
public abstract class BaseConfigMigrateMapper extends AbstractMapper implements ConfigMigrateMapper {

    @Override
    public String getFunction(String functionName) {
        return DatabaseDialectManager.getInstance().getDialect(getDataSource()).getFunction(functionName);
    }
}
