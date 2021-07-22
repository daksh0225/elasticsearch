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

import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SandboxHandlerPlugin extends Plugin implements ActionPlugin{

    private final SandboxSettingsConfig config;
    private boolean sandboxLoaded;
    private final Settings settings;
    static SandboxService sandboxService;

    public SandboxHandlerPlugin(final Settings settings, final Path configPath){
        this.settings = settings;
        this.config = new SandboxSettingsConfig(new Environment(settings, configPath));
        this.sandboxLoaded = false;
    }
    @Override
    public List<RestHandler> getRestHandlers(final Settings settings,
                                             final RestController restController,
                                             final ClusterSettings clusterSettings,
                                             final IndexScopedSettings indexScopedSettings,
                                             final SettingsFilter settingsFilter,
                                             final IndexNameExpressionResolver indexNameExpressionResolver,
                                             final Supplier<DiscoveryNodes> nodesInCluster) {
        ArrayList<RestHandler> list = new ArrayList<>();
        list.add(new SandboxAction());
        list.add(new SandboxIndicesAction());
        return list;
    }

    @Override
    public List<Setting<?>> getSettings(){
        return Arrays.asList(SandboxSettingsConfig.SANDBOX_PERSIST_SETTING);
    }

    @Override
    public Settings additionalSettings(){
        final Settings.Builder builder = Settings.builder();

        builder.put(SandboxSettingsConfig.SANDBOX_PERSIST_SETTING.getKey(), config.getPersist());

        return builder.build();
    }

    @Override
    public UnaryOperator<RestHandler> getRestHandlerWrapper(ThreadContext threadContext){
        return originalHandler -> (RestHandler) (request, channel, client) -> {
            if(!sandboxLoaded && settings.getAsBoolean("sandbox.persist", false)) {
                sandboxService.loadSandboxes(client);
                sandboxLoaded = true;
            }
            request = handleIndicesName(request, client, handleSandboxHeader(request));
            originalHandler.handleRequest(request, channel, client);
        };
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool,
                                               ResourceWatcherService resourceWatcherService, ScriptService scriptService,
                                               NamedXContentRegistry xContentRegistry, Environment environment,
                                               NodeEnvironment nodeEnvironment, NamedWriteableRegistry namedWriteableRegistry,
                                               IndexNameExpressionResolver indexNameExpressionResolver) {
        sandboxService = new SandboxService((NodeClient) client, settings.getAsBoolean("sandbox.persist", false));
        return Collections.singletonList(sandboxService);
    }

    static String handleSandboxHeader(RestRequest request){
        if(request.getHeaders().containsKey("Sandbox")){
            String sandboxId = request.getHeaders().get("Sandbox").get(0);
            if(!sandboxService.sandboxContains(sandboxId))
                throw new IllegalArgumentException("Invalid Sandbox Id provided");
            return sandboxId;
        }
        else
            return null;
    }

    static RestRequest handleIndicesName(RestRequest request, NodeClient client, String sandboxId){
        if(request.params().containsKey("index")) {
            String[] indices = Strings.splitStringByCommaToArray(request.params().get("index"));
            for(int i=0;i<indices.length;i++){
                String clusterName = null;
                if(indices[i].contains(":")) {
                    String[] slices = Strings.split(indices[i], ":");
                    assert slices != null;
                    indices[i] = slices[1];
                    clusterName = slices[0];
                }
                if(!(indices[i].equals("*") || indices[i].equals("_all"))){
                    if(sandboxId != null) {
                        sandboxService.addSandboxIndex(sandboxId, indices[i]);
                        indices[i] = "sandbox_index_" + sandboxId + "_" + indices[i];
                    }
                    else
                        indices[i] = "global_index_" + indices[i];
                }
                if(clusterName != null)
                    indices[i] = clusterName+":"+indices[i];
            }
            String newIndexParam = Strings.arrayToCommaDelimitedString(indices);
            request.params().replace("index", newIndexParam);
        }
        BytesReference content = request.content();
        request.hasContent();
        String contentString = content.utf8ToString();
        if(contentString.contains("\"_index\":") || contentString.contains("\"_index\" :")){
            if(sandboxId == null)
                contentString = contentString.replaceAll("(\"_index\".*:)(.*\")(.*\",)", "$1$2global_index_$3");
            else {
                List<String> allMatches = new ArrayList<>();
                Matcher m = Pattern.compile("(\"_index\".*:)(.*\")(.*\",)").matcher(contentString);
                while(m.find()){
                    allMatches.add(m.group(3).replace("\",", ""));
                }
                for(String index: allMatches){
                    sandboxService.addSandboxIndex(sandboxId, index);
                }
                contentString = contentString.replaceAll("(\"_index\".*:)(.*\")(.*\",)", "$1$2sandbox_index_" + sandboxId + "_$3");
            }
            request.updateContent(new BytesArray(contentString));
        }
        return request;
    }
}
