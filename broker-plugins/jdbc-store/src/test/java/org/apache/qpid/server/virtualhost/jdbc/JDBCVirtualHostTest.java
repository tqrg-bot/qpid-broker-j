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

package org.apache.qpid.server.virtualhost.jdbc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.BrokerTestHelper;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.test.utils.QpidTestCase;

public class JDBCVirtualHostTest extends QpidTestCase
{
    private CurrentThreadTaskExecutor _taskExecutor;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
    }

    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
        _taskExecutor.stopImmediately();
    }

    public void testInvalidTableNamePrefix() throws Exception
    {
        final VirtualHostNode vhn = BrokerTestHelper.mockWithSystemPrincipal(VirtualHostNode.class);
        when(vhn.getCategoryClass()).thenReturn(VirtualHostNode.class);
        when(vhn.getChildExecutor()).thenReturn(_taskExecutor);
        final ConfiguredObjectFactoryImpl factory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());
        when(vhn.getObjectFactory()).thenReturn(factory);
        when(vhn.getModel()).thenReturn(factory.getModel());

        EventLogger eventLogger = mock(EventLogger.class);
        SystemConfig systemConfig = mock(SystemConfig.class);
        when(systemConfig.getEventLogger()).thenReturn(eventLogger);
        Broker broker = mock(Broker.class);
        when(broker.getParent()).thenReturn(systemConfig);
        when(vhn.getParent()).thenReturn(broker);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, getTestName());
        attributes.put(ConfiguredObject.TYPE, JDBCVirtualHostImpl.VIRTUAL_HOST_TYPE);
        attributes.put("connectionUrl", "jdbc://example.com");
        JDBCVirtualHost<?> jdbcVirtualHost = new JDBCVirtualHostImpl(attributes, vhn);

        // This list is not exhaustive
        List<String> knownInvalidPrefixes = Arrays.asList("with\"dblquote",
                                                          "with'quote",
                                                          "with-dash",
                                                          "with;semicolon",
                                                          "with space",
                                                          "with%percent",
                                                          "with|pipe",
                                                          "with(paren",
                                                          "with)paren",
                                                          "with[bracket",
                                                          "with]bracket",
                                                          "with{brace",
                                                          "with}brace");
        for (String invalidPrefix : knownInvalidPrefixes)
        {
            try
            {
                jdbcVirtualHost.setAttributes(Collections.<String, Object>singletonMap("tableNamePrefix",
                                                                                       invalidPrefix));
                fail(String.format("Should not be able to set prefix to '%s'", invalidPrefix));
            }
            catch (IllegalConfigurationException e)
            {
                // pass
            }
        }
    }
}
