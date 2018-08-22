/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infinispan.jdbc;

import org.teiid.cache.Cache;
import org.teiid.core.util.LRUCache;
import org.teiid.runtime.EmbeddedServer;

public class TeiidServer extends EmbeddedServer {

    public boolean hasConnectorManagerRepository(String name) {
        return this.cmr.getConnectorManager(name) != null;
    }

    static class LocalCache<K, V> extends LRUCache<K, V> implements Cache<K, V> {
        private static final long serialVersionUID = -7894312381042966398L;
        private String name;

        public LocalCache(String cacheName, int maxSize) {
            super(maxSize < 0 ? Integer.MAX_VALUE : maxSize);
            this.name = cacheName;
        }

        @Override
        public V put(K key, V value, Long ttl) {
            return put(key, value);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public boolean isTransactional() {
            return false;
        }
    }
}