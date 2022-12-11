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

package org.apache.shenyu.plugin.sign.api;

import org.apache.shenyu.common.utils.SignUtils;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The Sign plugin default signer.
 */
public class DefaultSignProvider implements SignProvider {

    /**
     * acquired sign.
     *
     * @param signKey     sign key
     * @param jsonParams  json params
     * @param queryParams url query params
     * @return sign
     */
    @Override
    public String generateSign(final String signKey, final Map<String, String> jsonParams, final Map<String, String> queryParams) {

        final String jsonSign = Optional.ofNullable(jsonParams).map(e -> e.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .map(key -> String.join("", key, jsonParams.get(key)))
                .collect(Collectors.joining()).trim())
                .orElse("");

        final String querySign = Optional.ofNullable(queryParams).map(e -> e.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .map(key -> String.join("", key, queryParams.get(key)))
                .collect(Collectors.joining()).trim())
                .orElse("");

        final String data = String.join("", jsonSign, querySign);

        return SignUtils.sign(SignUtils.SIGN_MD5, signKey, data).toUpperCase();
    }
}
