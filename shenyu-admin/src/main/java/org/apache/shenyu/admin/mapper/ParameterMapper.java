/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shenyu.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.shenyu.admin.model.entity.ParameterDO;

/**
 * ParameterMapper.
 */
@Mapper
public interface ParameterMapper {

    /**
     * select by parameter id.
     *
     * @param id primary key
     * @return ParameterDo
     */
    ParameterDO selectByPrimaryKey(String id);

    /**
     * insert parameter.
     *
     * @param parameterDO the inserted parameterDO
     * @return counts of rows inserted
     */
    int insert(ParameterDO parameterDO);

    /**
     * insert parameter with selective fields.
     *
     * @param parameterDO the inserted parameterDO
     * @return counts of row inserted
     */
    int insertSelective(ParameterDO parameterDO);

    /**
     * update parameter.
     *
     * @param parameterDO the updated parameterDO
     * @return counts of row updated
     */
    int updateByPrimaryKey(ParameterDO parameterDO);

    /**
     * update parameter with selective fields.
     *
     * @param parameterDO the updated parameterDO
     * @return counts of row updated
     */
    int updateByPrimaryKeySelective(ParameterDO parameterDO);

    /**
     * delete parameter.
     *
     * @param id the primary key
     * @return counts of row deleted
     */
    int deleteByPrimaryKey(String id);
}
