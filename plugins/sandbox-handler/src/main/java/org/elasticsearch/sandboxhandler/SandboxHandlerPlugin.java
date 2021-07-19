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

import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.settings.*;
import org.elasticsearch.env.Environment;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class SandboxHandlerPlugin extends Plugin implements ActionPlugin {

    private final SandboxSettingsConfig config;

    public SandboxHandlerPlugin(final Settings settings, final Path configPath){
        this.config = new SandboxSettingsConfig(new Environment(settings, configPath));
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
}
