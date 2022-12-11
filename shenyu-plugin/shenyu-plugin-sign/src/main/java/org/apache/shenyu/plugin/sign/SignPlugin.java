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

package org.apache.shenyu.plugin.sign;

import org.apache.commons.lang3.StringUtils;
import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.utils.JsonUtils;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.api.result.ShenyuResultEnum;
import org.apache.shenyu.plugin.api.result.ShenyuResultWrap;
import org.apache.shenyu.plugin.api.utils.WebFluxResultUtils;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.base.support.BodyInserterContext;
import org.apache.shenyu.plugin.base.support.CachedBodyOutputMessage;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.base.utils.ResponseUtils;
import org.apache.shenyu.plugin.sign.api.SignService;
import org.apache.shenyu.plugin.sign.decorator.SignRequestDecorator;
import org.apache.shenyu.plugin.sign.handler.SignPluginDataHandler;
import org.apache.shenyu.plugin.sign.handler.SignRuleHandler;
import org.apache.shenyu.plugin.sign.api.VerifyResult;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Sign Plugin.
 */
public class SignPlugin extends AbstractShenyuPlugin {

    private final List<HttpMessageReader<?>> messageReaders;

    private final SignService signService;

    /**
     * Instantiates a new Sign plugin.
     *
     * @param readers     the sign use readers
     * @param signService the sign service
     */
    public SignPlugin(final List<HttpMessageReader<?>> readers, final SignService signService) {
        this.signService = signService;
        messageReaders = readers;
    }

    @Override
    public String named() {
        return PluginEnum.SIGN.getName();
    }

    @Override
    public int getOrder() {
        return PluginEnum.SIGN.getCode();
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange, final ShenyuPluginChain chain, final SelectorData selectorData, final RuleData rule) {
        SignRuleHandler ruleHandler = SignPluginDataHandler.CACHED_HANDLE.get().obtainHandle(CacheKeyUtils.INST.getKey(rule));
        if (ObjectUtils.isEmpty(ruleHandler) || !ruleHandler.getSignRequestBody()) {
            VerifyResult result = signService.signVerify(exchange);
            if (result.isFailed()) {
                return handleSignFailed(exchange, result.getReason());
            }
            return chain.execute(exchange);
        }

        ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);
        return serverRequest.bodyToMono(String.class)
                .switchIfEmpty(Mono.defer(() -> Mono.just("")))
                .flatMap(originalBody -> {
                    VerifyResult result = signBody(originalBody, exchange);
                    if (result.isSuccess()) {
                        return executeChain(originalBody, exchange, chain);
                    }
                    return handleSignFailed(exchange, result.getReason());
                });
    }

    private VerifyResult signBody(final String originalBody, final ServerWebExchange exchange) {
        // get url params
        MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
        // get post body
        Map<String, Object> requestBody = StringUtils.isBlank(originalBody) ? null : JsonUtils.jsonToMap(originalBody);
        Map<String, String> queryParamsSingleValueMap = queryParams.toSingleValueMap();
        return signService.signVerify(exchange, requestBody, queryParamsSingleValueMap);
    }

    private Mono<Void> executeChain(final String requestBody, final ServerWebExchange exchange, final ShenyuPluginChain chain) {
        BodyInserter<String, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromValue(requestBody);
        CachedBodyOutputMessage outputMessage = ResponseUtils.newCachedBodyOutputMessage(exchange);
        return bodyInserter.insert(outputMessage, new BodyInserterContext())
                .then(Mono.defer(() -> {
                        ServerHttpRequestDecorator decorator = new SignRequestDecorator(exchange, outputMessage);
                        return chain.execute(exchange.mutate().request(decorator).build());
                    }
                )).onErrorResume((Function<Throwable, Mono<Void>>) throwable -> ResponseUtils.release(outputMessage, throwable));
    }

    private Mono<Void> handleSignFailed(final ServerWebExchange exchange, final String reason) {
        Object error = ShenyuResultWrap.error(exchange, ShenyuResultEnum.SIGN_IS_NOT_PASS.getCode(), reason, null);
        return WebFluxResultUtils.result(exchange, error);
    }
}
