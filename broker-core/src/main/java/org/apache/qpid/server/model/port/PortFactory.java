/*
 *
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
 *
 */
package org.apache.qpid.server.model.port;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Protocol.ProtocolType;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;
import org.apache.qpid.server.util.MapValueConverter;

public class PortFactory<X extends Port<X>> implements ConfiguredObjectTypeFactory<X>
{
    public static final int DEFAULT_AMQP_SEND_BUFFER_SIZE = 262144;
    public static final int DEFAULT_AMQP_RECEIVE_BUFFER_SIZE = 262144;
    public static final boolean DEFAULT_AMQP_NEED_CLIENT_AUTH = false;
    public static final boolean DEFAULT_AMQP_WANT_CLIENT_AUTH = false;
    public static final boolean DEFAULT_AMQP_TCP_NO_DELAY = true;
    public static final String DEFAULT_AMQP_BINDING = "*";
    public static final Transport DEFAULT_TRANSPORT = Transport.TCP;
    private ConfiguredObjectFactory _configuredObjectFactory;


    public PortFactory()
    {
    }

    public Port createPort(UUID id, Broker broker, Map<String, Object> attributes)
    {
        attributes = new HashMap<String, Object>(attributes);
        attributes.put(Port.ID, id);
        return create(attributes,broker);
    }

    private ProtocolType getProtocolType(Map<String, Object> portAttributes)
    {

        Set<Protocol> protocols = MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, portAttributes, Protocol.class);

        ProtocolType protocolType = null;

        if(protocols == null || protocols.isEmpty())
        {
            // defaulting to AMQP if protocol is not specified
            protocolType = ProtocolType.AMQP;
        }
        else
        {
            for (Protocol protocol : protocols)
            {
                if (protocolType == null)
                {
                    protocolType = protocol.getProtocolType();
                }
                else if (protocolType != protocol.getProtocolType())
                {

                    throw new IllegalConfigurationException("Found different protocol types '" + protocolType
                                                            + "' and '" + protocol.getProtocolType()
                                                            + "' for port configuration: " + portAttributes);

                }
            }
        }

        return protocolType;
    }


    @Override
    public Class<? super Port> getCategoryClass()
    {
        return Port.class;
    }

    @Override
    public X create(final Map<String, Object> attributes, final ConfiguredObject<?>... parents)
    {
        return getPortFactory(attributes).create(attributes,parents);
    }

    @Override
    public UnresolvedConfiguredObject<X> recover(final ConfiguredObjectRecord record,
                                                 final ConfiguredObject<?>... parents)
    {
        return getPortFactory(record.getAttributes()).recover(record, parents);
    }

    public ConfiguredObjectTypeFactory<X> getPortFactory(Map<String,Object> attributes)
    {
        String type;

        if(attributes.containsKey(Port.TYPE))
        {
            type = (String) attributes.get(Port.TYPE);
        }
        else
        {
            type = getProtocolType(attributes).name();
        }

        synchronized (this)
        {
            if(_configuredObjectFactory == null)
            {
                _configuredObjectFactory = new ConfiguredObjectFactory(Model.getInstance());
            }
        }
        return _configuredObjectFactory.getConfiguredObjectTypeFactory(Port.class.getSimpleName(), type);
    }

    private Broker getBroker(final ConfiguredObject<?>[] parents)
    {
        if(parents.length != 1 || !(parents[0] instanceof Broker))
        {
            throw new IllegalConfigurationException("Port should have exactly one parent, of type Broker");
        }
        return (Broker<?>) parents[0];
    }

    @Override
    public String getType()
    {
        return null;
    }
}
