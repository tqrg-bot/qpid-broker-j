/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.qpid.server.security.auth.manager;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.ManagedContextDefault;

public interface CachingAuthenticationProvider<X extends AuthenticationProvider<X>> extends AuthenticationProvider<X>
{
    String AUTHORISATION_CACHE_MAX_SIZE = "qpid.auth.cache.size";
    @SuppressWarnings("unused")
    @ManagedContextDefault(name = AUTHORISATION_CACHE_MAX_SIZE,
            description = "Upper bound of authentication results the AuthenticationProvider will cache.")
    long DEFAULT_AUTHORISATION_CACHE_MAX_SIZE = 100;

    String AUTHORISATION_CACHE_EXPIRATION_TIME = "qpid.auth.cache.expirationTime";
    @SuppressWarnings("unused")
    @ManagedContextDefault(name = AUTHORISATION_CACHE_EXPIRATION_TIME,
            description = "How long cached credentials are valid in seconds.")
    long DEFAULT_AUTHORISATION_CACHE_EXPIRATION_TIME = 10 * 60;
}
