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

package org.apache.shenyu.plugin.sign.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.shenyu.common.constant.Constants;
import org.apache.shenyu.common.dto.AppAuthData;
import org.apache.shenyu.common.dto.AuthParamData;
import org.apache.shenyu.common.dto.AuthPathData;
import org.apache.shenyu.common.dto.PluginData;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.utils.MapUtils;
import org.apache.shenyu.common.utils.SignUtils;
import org.apache.shenyu.plugin.api.context.ShenyuContext;
import org.apache.shenyu.plugin.api.result.ShenyuResultEnum;
import org.apache.shenyu.plugin.api.utils.SpringBeanUtils;
import org.apache.shenyu.plugin.base.cache.BaseDataCache;
import org.apache.shenyu.plugin.sign.api.DefaultSignProvider;
import org.apache.shenyu.plugin.sign.api.SignProvider;
import org.apache.shenyu.plugin.sign.api.SignService;
import org.apache.shenyu.plugin.sign.api.VerifyResult;
import org.apache.shenyu.plugin.sign.cache.SignAuthDataCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DefaultSignService Test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class DefaultSignServiceTest {

    private SignService signService;

    private ServerWebExchange exchange;

    private final String appKey = "D1DFC83F3BC64FABB89DFBD54E5A28C8";

    private final String secretKey = "692C479F98C841FCBEB444B7CA775F63";

    private ShenyuContext passed;

    @Value("${shenyu.sign.delay:5}")
    private int delay;

    @BeforeEach
    public void setup() {
        this.signService = new DefaultSignService();
        this.exchange = MockServerWebExchange.from(MockServerHttpRequest.get("localhost").build());

        final String path = "/test-api/demo/test";
        PluginData signData = new PluginData();
        signData.setId("1");
        signData.setName(PluginEnum.SIGN.getName());
        signData.setEnabled(true);
        signData.setRole("1");
        BaseDataCache.getInstance().cachePluginData(signData);

        AppAuthData authData = new AppAuthData();
        authData.setAppKey(appKey);
        authData.setAppSecret(secretKey);
        authData.setEnabled(true);
        authData.setOpen(true);
        AuthPathData authPathData = new AuthPathData();
        authPathData.setAppName("test-api");
        authPathData.setPath(path);
        authPathData.setEnabled(true);
        authData.setPathDataList(Lists.newArrayList(authPathData));
        SignAuthDataCache.getInstance().cacheAuthData(authData);

        this.passed = new ShenyuContext();
        this.passed.setModule("/test-api");
        this.passed.setMethod("/demo/test");
        this.passed.setRpcType("springCloud");
        this.passed.setHttpMethod("GET");
        this.passed.setPath(path);
        final String timestamp = String.valueOf(System.currentTimeMillis());
        this.passed.setTimestamp(timestamp);
        this.passed.setSign(buildSign(this.secretKey, timestamp, this.passed.getPath(), null, null));
        this.passed.setAppKey(appKey);
        this.passed.setContextPath("/test-api");
        this.passed.setRealUrl("/demo/test");

        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        SpringBeanUtils.getInstance().setApplicationContext(context);
        when(context.getBean(SignProvider.class)).thenReturn(new DefaultSignProvider());
    }

    @Test
    public void normalTest() {
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.success());
    }

    @Test
    public void nullTimestampTest() {
        this.passed.setTimestamp(null);
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_PARAMS_ERROR));
    }

    @Test
    public void nullSignTest() {
        this.passed.setSign(null);
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);
        AppAuthData authData = SignAuthDataCache.getInstance().obtainAuthData(appKey);
        authData.setPathDataList(Collections.emptyList());
        SignAuthDataCache.getInstance().cacheAuthData(authData);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_PARAMS_ERROR));
    }

    @Test
    public void nullAppKeyTest() {
        this.passed.setAppKey(null);
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_PARAMS_ERROR));
    }

    @Test
    public void overdueTest() {
        long errorTimestamp = Long.parseLong(this.passed.getTimestamp()) - ((long) (delay + 1) * 1000 * 60);
        this.passed.setTimestamp(Long.toString(errorTimestamp));
        this.passed.setSign(buildSign(this.secretKey, this.passed.getTimestamp(), this.passed.getPath(), null, null));
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.fail(String.format(ShenyuResultEnum.SIGN_TIME_IS_TIMEOUT.getMsg(), delay)));
    }

    @Test
    public void errorAppKeyTest() {
        this.passed.setAppKey("errorKey");
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_APP_KEY_IS_NOT_EXIST));
    }

    @Test
    public void emptyAuthPath() {
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);
        AppAuthData authData = SignAuthDataCache.getInstance().obtainAuthData(appKey);
        authData.setPathDataList(Collections.emptyList());
        SignAuthDataCache.getInstance().cacheAuthData(authData);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_PATH_NOT_EXIST));
    }

    @Test
    public void fillParamPath() {
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);
        AppAuthData authData = SignAuthDataCache.getInstance().obtainAuthData(appKey);
        AuthParamData authParamData = new AuthParamData();
        authParamData.setAppParam("appParam");
        authParamData.setAppName("appParam");
        authData.setParamDataList(Collections.singletonList(authParamData));
        SignAuthDataCache.getInstance().cacheAuthData(authData);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.success());
    }

    @Test
    public void emptyParamPath() {
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);
        AppAuthData authData = SignAuthDataCache.getInstance().obtainAuthData(appKey);
        SignAuthDataCache.getInstance().cacheAuthData(authData);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.success());
    }

    @Test
    public void errorAuthPath() {
        this.passed.setPath("errorPath");
        this.passed.setSign(buildSign(this.secretKey, this.passed.getTimestamp(), this.passed.getPath(), null, null));
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_PATH_NOT_EXIST));
    }

    @Test
    public void errorSign() {
        this.passed.setSign("errorSign");
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);

        VerifyResult ret = this.signService.signVerify(this.exchange);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_VALUE_IS_ERROR));
    }

    @Test
    public void errorBodySign() {
        this.passed.setSign("errorSign");
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);
        Map<String, Object> requestBody = Maps.newHashMapWithExpectedSize(1);
        requestBody.put("data", "data");
        VerifyResult ret = this.signService.signVerify(this.exchange, requestBody, null);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_VALUE_IS_ERROR));
    }

    @Test
    public void bodySign() {
        Map<String, Object> requestBody = Maps.newHashMapWithExpectedSize(1);
        requestBody.put("data", "data");
        this.passed.setSign(buildSign(this.secretKey, this.passed.getTimestamp(), this.passed.getPath(), MapUtils.transStringMap(requestBody), null));
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);
        VerifyResult ret = this.signService.signVerify(this.exchange, requestBody, null);
        assertEquals(ret, VerifyResult.success());
    }

    @Test
    public void bodyAndUrlQueryParamsSign() {
        Map<String, Object> requestBody = Maps.newHashMapWithExpectedSize(1);
        requestBody.put("data", "data");
        Map<String, String> queryParams = Maps.newHashMapWithExpectedSize(1);
        queryParams.put("data2", "data");
        this.passed.setSign(buildSign(this.secretKey, this.passed.getTimestamp(), this.passed.getPath(), MapUtils.transStringMap(requestBody), queryParams));
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);
        VerifyResult ret = this.signService.signVerify(this.exchange, requestBody, queryParams);
        assertEquals(ret, VerifyResult.success());
    }

    @Test
    public void errorBodyAndUrlQueryParamsSign() {
        Map<String, Object> requestBody = Maps.newHashMapWithExpectedSize(1);
        requestBody.put("data", "data");
        Map<String, String> queryParams = Maps.newHashMapWithExpectedSize(1);
        queryParams.put("data", "data");
        this.passed.setSign(buildSign(this.secretKey, this.passed.getTimestamp(), this.passed.getPath(), MapUtils.transStringMap(requestBody), null));
        this.exchange.getAttributes().put(Constants.CONTEXT, this.passed);
        // Tamper with request body parameters
        requestBody.put("data", "data2");
        VerifyResult ret = this.signService.signVerify(this.exchange, requestBody, queryParams);
        assertEquals(ret, VerifyResult.fail(Constants.SIGN_VALUE_IS_ERROR));
    }

    private String buildSign(final String signKey, final String timeStamp, final String path, final Map<String, String> jsonParams, final Map<String, String> queryParams) {

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

        final String extSignKey = String.join("", Constants.TIMESTAMP, timeStamp, Constants.PATH, path, Constants.VERSION, "1.0.0", signKey);
        final String data = String.join("", jsonSign, querySign);
        return SignUtils.sign(SignUtils.SIGN_MD5, extSignKey, data).toUpperCase();
    }
}
