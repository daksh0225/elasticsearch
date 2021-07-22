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

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;

import java.io.IOException;
import java.nio.file.Path;

public class SandboxSettingsConfig {

    static final Setting<Boolean> SANDBOX_PERSIST_SETTING =
        Setting.boolSetting("sandbox.persist", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    private final Boolean persist;

    public SandboxSettingsConfig(final Environment environment){
        final Path configDir = environment.configFile();

        final Path sandboxSettingsYamlFile = configDir.resolve("elasticsearch.yml");

        final Settings customSettings;
        try {
            customSettings = Settings.builder().loadFromPath(sandboxSettingsYamlFile).build();
            assert customSettings != null;
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to load settings", e);
        }

        this.persist = SANDBOX_PERSIST_SETTING.get(customSettings);
    }

    public Boolean getPersist(){
        return persist;
    }
}
