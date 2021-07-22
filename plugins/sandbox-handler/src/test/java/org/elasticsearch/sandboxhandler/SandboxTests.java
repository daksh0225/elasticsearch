/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.sandboxhandler;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.test.rest.FakeRestChannel;
import org.elasticsearch.test.rest.FakeRestRequest;
import org.elasticsearch.test.rest.RestActionTestCase;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.elasticsearch.mock.orig.Mockito.mock;
import static org.elasticsearch.sandboxhandler.SandboxHandlerPlugin.handleIndicesName;
import static org.elasticsearch.sandboxhandler.SandboxHandlerPlugin.handleSandboxHeader;
import static org.elasticsearch.sandboxhandler.SandboxHandlerPlugin.sandboxService;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.object.HasToString.hasToString;

public class SandboxTests extends RestActionTestCase {

    @BeforeClass
    public static void setup(){
        sandboxService = new SandboxService();
    }

    public void testInvalidSandbox() {
        final AtomicBoolean executed = new AtomicBoolean();
        final BaseRestHandler handler = new BaseRestHandler() {
            @Override
            protected RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
                request.param("index");
                handleIndicesName(request, client, handleSandboxHeader(request));
                return channel -> executed.set(true);
            }

            @Override
            public String getName() {
                return "test_invalid_sandbox";
            }

            @Override
            public List<Route> routes() {
                return Collections.emptyList();
            }
        };

        HashMap<String, List<String>> headers = new HashMap<>() ;
        headers.put("Sandbox", Arrays.asList("abcdefghijklmnopqrstuv"));

        HashMap<String, String> params = new HashMap<>();
        params.put("index", "test_index");

        final RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withHeaders(headers)
            .withParams(params)
            .withPath("/test_index")
            .build();

        final RestChannel channel = new FakeRestChannel(request, randomBoolean(), 1);
        final IllegalArgumentException e =
            expectThrows(IllegalArgumentException.class, () -> handler.handleRequest(request, channel, mock(NodeClient.class)));
        assertThat(e, hasToString(containsString("Invalid Sandbox Id provided")));
        assertFalse(executed.get());
    }

    public void testIndexNamePrefixWithoutSandboxHeader() throws Exception {
        final AtomicBoolean executed = new AtomicBoolean();
        final BaseRestHandler handler = new BaseRestHandler() {
            @Override
            protected RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
                request.param("index");
                RestRequest newRequest = handleIndicesName(request, client, handleSandboxHeader(request));
                assert newRequest.param("index").contains("global_index_");
                return channel -> executed.set(true);
            }

            @Override
            public String getName() {
                return "test_index_name_prefix_without_sandbox_header";
            }

            @Override
            public List<Route> routes() {
                return Collections.emptyList();
            }
        };

        HashMap<String, String> params = new HashMap<>();
        params.put("index", "test-index");

        final RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withParams(params)
            .withPath("/test-index")
            .build();

        final RestChannel channel = new FakeRestChannel(request, randomBoolean(), 1);
        handler.handleRequest(request, channel, mock(NodeClient.class));
        assertTrue(executed.get());
    }

    public void testValidSandboxAndIndexNamePrefix() throws Exception {
        final AtomicBoolean executed = new AtomicBoolean();
        final BaseRestHandler handler = new BaseRestHandler() {
            @Override
            protected RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
                request.param("index");
                RestRequest newRequest = handleIndicesName(request, client, handleSandboxHeader(request));
                assert newRequest.param("index").contains("sandbox_index_");
                return channel -> executed.set(true);
            }

            @Override
            public String getName() {
                return "test_valid_sandbox_and_index_name_prefix";
            }

            @Override
            public List<Route> routes() {
                return Collections.emptyList();
            }
        };

        HashMap<String, List<String>> headers = new HashMap<>() ;
        headers.put("Sandbox", Arrays.asList(sandboxService.getSandbox()));

        HashMap<String, String> params = new HashMap<>();
        params.put("index", "test-index");

        final RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withHeaders(headers)
            .withParams(params)
            .withPath("/test-index")
            .build();

        final RestChannel channel = new FakeRestChannel(request, randomBoolean(), 1);
        handler.handleRequest(request, channel, mock(NodeClient.class));
        assertTrue(executed.get());
    }
}
