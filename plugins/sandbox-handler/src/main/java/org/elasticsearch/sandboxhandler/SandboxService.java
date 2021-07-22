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

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.component.LifecycleListener;
import org.elasticsearch.search.SearchHit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class SandboxService implements LifecycleComponent {

    private HashMap<String, Sandbox> sandboxes;
    private NodeClient client;
    private boolean sandboxPersist;

    public SandboxService(){
        this.client = null;
        this.sandboxes = new HashMap<>();
    }

    public SandboxService(NodeClient client, boolean sandboxPersist){
        this.client = client;
        this.sandboxes = new HashMap<>();
        this.sandboxPersist = sandboxPersist;
    }

    public String getSandbox(){
        String id = UUIDs.randomBase64UUID().toLowerCase(Locale.ROOT);
        Sandbox sandbox = new Sandbox(id);
        sandboxes.put(id, sandbox);
        return sandbox.getUUID();
    }

    public void loadSandboxes(NodeClient client){
        ActionFuture<SearchResponse> response = client.search(new SearchRequest("global_index_sandboxes"));
        SearchResponse searchResponse = null;
        try {
            searchResponse = response.get();
        } catch (InterruptedException e) {
            e.getMessage();
        } catch (ExecutionException e) {
            e.getMessage();
        }
        if (searchResponse != null) {
            SearchHit[] hits = searchResponse.getInternalResponse().hits().getHits();
            for (SearchHit hit : hits) {
                String source = hit.getSourceAsString();
                if (source != null) {
                    String sandboxId = source.substring(14, 36);
                    sandboxes.put(sandboxId, new Sandbox(sandboxId));
                }
            }
        }
    }

    public Boolean sandboxContains(String sandboxId){
        return sandboxes.containsKey(sandboxId);
    }

    public void addSandboxIndex(String sandboxId, String indexName){
        sandboxes.get(sandboxId).addIndex(indexName);
    }

    public synchronized void removeSandboxes(NodeClient client){
        for(Map.Entry<String, Sandbox> entry: sandboxes.entrySet()){
            String id = entry.getKey();
            Sandbox sandbox = entry.getValue();

            Set<String> indices = sandbox.getIndices();
            for(String index: indices){
                client.admin().indices().delete(new DeleteIndexRequest("sandbox_index_"+id+"_"+index));
            }
        }
        sandboxes.clear();
    }

    @Override
    public Lifecycle.State lifecycleState() {
        return null;
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        //nothing to do
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        //nothing to do
    }

    @Override
    public void start() {
        //nothing to do
    }

    @Override
    public void stop() {
        doStop();
    }

    public void doStop(){
        if(!sandboxPersist)
            removeSandboxes(client);
    }

    @Override
    public void close() {
        //nothing to do
    }
}
