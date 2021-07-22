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

import java.util.HashSet;
import java.util.Set;

public class Sandbox {

    private final String id;
    private Set<String> indices;

    public Sandbox(String id){
        this.id = id;
        this.indices = new HashSet<>();
    }

    public String getUUID(){
        return id;
    }

    public void addIndex(String index){
        indices.add(index);
    }

    public Set<String> getIndices(){
        return indices;
    }
}
