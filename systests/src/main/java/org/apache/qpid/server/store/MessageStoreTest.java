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
package org.apache.qpid.server.store;


import java.util.ArrayList;
import java.util.Collection;
import org.apache.commons.configuration.PropertiesConfiguration;

import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.amqp_8_0.BasicConsumeBodyImpl;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.protocol.v0_8.MessageMetaData;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQPriorityQueue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.ConflationQueue;
import org.apache.qpid.server.queue.IncomingMessage;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This tests the MessageStores by using the available interfaces.
 *
 * For persistent stores, it validates that Exchanges, Queues, Bindings and
 * Messages are persisted and recovered correctly.
 */
public class MessageStoreTest extends QpidTestCase
{
    public static final int DEFAULT_PRIORTY_LEVEL = 5;
    public static final String SELECTOR_VALUE = "Test = 'MST'";
    public static final String LVQ_KEY = "MST-LVQ-KEY";

    private String nonDurableExchangeName = "MST-NonDurableDirectExchange";
    private String directExchangeName = "MST-DirectExchange";
    private String topicExchangeName = "MST-TopicExchange";

    private AMQShortString durablePriorityTopicQueueName = new AMQShortString("MST-PriorityTopicQueue-Durable");
    private AMQShortString durableTopicQueueName = new AMQShortString("MST-TopicQueue-Durable");
    private AMQShortString priorityTopicQueueName = new AMQShortString("MST-PriorityTopicQueue");
    private AMQShortString topicQueueName = new AMQShortString("MST-TopicQueue");

    private AMQShortString durableExclusiveQueueName = new AMQShortString("MST-Queue-Durable-Exclusive");
    private AMQShortString durablePriorityQueueName = new AMQShortString("MST-PriorityQueue-Durable");
    private AMQShortString durableLastValueQueueName = new AMQShortString("MST-LastValueQueue-Durable");
    private AMQShortString durableQueueName = new AMQShortString("MST-Queue-Durable");
    private AMQShortString priorityQueueName = new AMQShortString("MST-PriorityQueue");
    private AMQShortString queueName = new AMQShortString("MST-Queue");

    private AMQShortString directRouting = new AMQShortString("MST-direct");
    private AMQShortString topicRouting = new AMQShortString("MST-topic");

    private AMQShortString queueOwner = new AMQShortString("MST");

    private PropertiesConfiguration _config;

    private VirtualHost _virtualHost;
    private org.apache.qpid.server.model.VirtualHost _virtualHostModel;
    private Broker _broker;
    private String _storePath;

    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();

        _storePath = System.getProperty("QPID_WORK") + File.separator + getName();

        _config = new PropertiesConfiguration();
        _config.addProperty("store.class", getTestProfileMessageStoreClassName());
        _config.addProperty("store.environment-path", _storePath);
        _virtualHostModel = mock(org.apache.qpid.server.model.VirtualHost.class);
        when(_virtualHostModel.getAttribute(eq(org.apache.qpid.server.model.VirtualHost.STORE_PATH))).thenReturn(_storePath);



        cleanup(new File(_storePath));

        _broker = BrokerTestHelper.createBrokerMock();

        reloadVirtualHost();
    }

    protected String getStorePath()
    {
        return _storePath;
    }

    protected org.apache.qpid.server.model.VirtualHost getVirtualHostModel()
    {
        return _virtualHostModel;
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHost != null)
            {
                _virtualHost.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public PropertiesConfiguration getConfig()
    {
        return _config;
    }

    protected void reloadVirtualHost()
    {
        VirtualHost original = getVirtualHost();

        if (getVirtualHost() != null)
        {
            try
            {
                getVirtualHost().close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }

        try
        {
            _virtualHost = BrokerTestHelper.createVirtualHost(new VirtualHostConfiguration(getClass().getName(), _config, _broker),null,getVirtualHostModel());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }

        assertTrue("Virtualhost has not changed, reload was not successful", original != getVirtualHost());
    }

    /**
     * Old MessageStoreTest segment which runs against both persistent and non-persistent stores
     * creating queues, exchanges and bindings and then verifying message delivery to them.
     */
    public void testQueueExchangeAndBindingCreation() throws Exception
    {
        assertEquals("Should not be any existing queues", 0,  getVirtualHost().getQueueRegistry().getQueues().size());

        createAllQueues();
        createAllTopicQueues();

        //Register Non-Durable DirectExchange
        Exchange nonDurableExchange = createExchange(DirectExchange.TYPE, nonDurableExchangeName, false);
        bindAllQueuesToExchange(nonDurableExchange, directRouting);

        //Register DirectExchange
        Exchange directExchange = createExchange(DirectExchange.TYPE, directExchangeName, true);
        bindAllQueuesToExchange(directExchange, directRouting);

        //Register TopicExchange
        Exchange topicExchange = createExchange(TopicExchange.TYPE, topicExchangeName, true);
        bindAllTopicQueuesToExchange(topicExchange, topicRouting);

        //Send Message To NonDurable direct Exchange = persistent
        sendMessageOnExchange(nonDurableExchange, directRouting, true);
        // and non-persistent
        sendMessageOnExchange(nonDurableExchange, directRouting, false);

        //Send Message To direct Exchange = persistent
        sendMessageOnExchange(directExchange, directRouting, true);
        // and non-persistent
        sendMessageOnExchange(directExchange, directRouting, false);

        //Send Message To topic Exchange = persistent
        sendMessageOnExchange(topicExchange, topicRouting, true);
        // and non-persistent
        sendMessageOnExchange(topicExchange, topicRouting, false);

        //Ensure all the Queues have four messages (one transient, one persistent) x 2 exchange routings
        validateMessageOnQueues(4, true);
        //Ensure all the topics have two messages (one transient, one persistent)
        validateMessageOnTopics(2, true);

        assertEquals("Not all queues correctly registered",
                10, getVirtualHost().getQueueRegistry().getQueues().size());
    }

    /**
     * Tests message persistence by running the testQueueExchangeAndBindingCreation() method above
     * before reloading the virtual host and ensuring that the persistent messages were restored.
     *
     * More specific testing of message persistence is left to store-specific unit testing.
     */
    public void testMessagePersistence() throws Exception
    {
        testQueueExchangeAndBindingCreation();

        reloadVirtualHost();

        //Validate durable queues and subscriptions still have the persistent messages
        validateMessageOnQueues(2, false);
        validateMessageOnTopics(1, false);
    }

    /**
     * Tests message removal by running the testMessagePersistence() method above before
     * clearing the queues, reloading the virtual host, and ensuring that the persistent
     * messages were removed from the queues.
     */
    public void testMessageRemoval() throws Exception
    {
        testMessagePersistence();

        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        assertEquals("Incorrect number of queues registered after recovery",
                6,  queueRegistry.getQueues().size());

        //clear the queue
        queueRegistry.getQueue(durableQueueName).clearQueue();

        //check the messages are gone
        validateMessageOnQueue(durableQueueName, 0);

        //reload and verify messages arent restored
        reloadVirtualHost();

        validateMessageOnQueue(durableQueueName, 0);
    }

    /**
     * Tests queue persistence by creating a selection of queues with differing properties, both
     * durable and non durable, and ensuring that following the recovery process the correct queues
     * are present and any property manipulations (eg queue exclusivity) are correctly recovered.
     */
    public void testQueuePersistence() throws Exception
    {
        assertEquals("Should not be any existing queues",
                0, getVirtualHost().getQueueRegistry().getQueues().size());

        //create durable and non durable queues/topics
        createAllQueues();
        createAllTopicQueues();

        //reload the virtual host, prompting recovery of the queues/topics
        reloadVirtualHost();

        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        assertEquals("Incorrect number of queues registered after recovery",
                6,  queueRegistry.getQueues().size());

        //Validate the non-Durable Queues were not recovered.
        assertNull("Non-Durable queue still registered:" + priorityQueueName,
                queueRegistry.getQueue(priorityQueueName));
        assertNull("Non-Durable queue still registered:" + queueName,
                queueRegistry.getQueue(queueName));
        assertNull("Non-Durable queue still registered:" + priorityTopicQueueName,
                queueRegistry.getQueue(priorityTopicQueueName));
        assertNull("Non-Durable queue still registered:" + topicQueueName,
                queueRegistry.getQueue(topicQueueName));

        //Validate normally expected properties of Queues/Topics
        validateDurableQueueProperties();

        //Update the durable exclusive queue's exclusivity
        setQueueExclusivity(false);
        validateQueueExclusivityProperty(false);
    }

    /**
     * Tests queue removal by creating a durable queue, verifying it recovers, and
     * then removing it from the store, and ensuring that following the second reload
     * process it is not recovered.
     */
    public void testDurableQueueRemoval() throws Exception
    {
        //Register Durable Queue
        createQueue(durableQueueName, false, true, false, false);

        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();
        assertEquals("Incorrect number of queues registered before recovery",
                1,  queueRegistry.getQueues().size());

        reloadVirtualHost();

        queueRegistry = getVirtualHost().getQueueRegistry();
        assertEquals("Incorrect number of queues registered after first recovery",
                1,  queueRegistry.getQueues().size());

        //test that removing the queue means it is not recovered next time
        final AMQQueue queue = queueRegistry.getQueue(durableQueueName);
        DurableConfigurationStoreHelper.removeQueue(getVirtualHost().getDurableConfigurationStore(),queue);

        reloadVirtualHost();

        queueRegistry = getVirtualHost().getQueueRegistry();
        assertEquals("Incorrect number of queues registered after second recovery",
                0,  queueRegistry.getQueues().size());
        assertNull("Durable queue was not removed:" + durableQueueName,
                queueRegistry.getQueue(durableQueueName));
    }

    /**
     * Tests exchange persistence by creating a selection of exchanges, both durable
     * and non durable, and ensuring that following the recovery process the correct
     * durable exchanges are still present.
     */
    public void testExchangePersistence() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        Map<String, Exchange> oldExchanges = createExchanges();

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 3, getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        //verify the exchanges present after recovery
        validateExchanges(origExchangeCount, oldExchanges);
    }

    /**
     * Tests exchange removal by creating a durable exchange, verifying it recovers, and
     * then removing it from the store, and ensuring that following the second reload
     * process it is not recovered.
     */
    public void testDurableExchangeRemoval() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        createExchange(DirectExchange.TYPE, directExchangeName, true);

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 1,  getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        assertEquals("Incorrect number of exchanges registered after first recovery",
                origExchangeCount + 1,  getVirtualHost().getExchanges().size());

        //test that removing the exchange means it is not recovered next time
        final Exchange exchange = getVirtualHost().getExchange(directExchangeName);
        DurableConfigurationStoreHelper.removeExchange(getVirtualHost().getDurableConfigurationStore(), exchange);

        reloadVirtualHost();

        assertEquals("Incorrect number of exchanges registered after second recovery",
                origExchangeCount,  getVirtualHost().getExchanges().size());
        assertNull("Durable exchange was not removed:" + directExchangeName,
                getVirtualHost().getExchange(directExchangeName));
    }

    /**
     * Tests binding persistence by creating a selection of queues and exchanges, both durable
     * and non durable, then adding bindings with and without selectors before reloading the
     * virtual host and verifying that following the recovery process the correct durable
     * bindings (those for durable queues to durable exchanges) are still present.
     */
    public void testBindingPersistence() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        createAllQueues();
        createAllTopicQueues();

        Map<String, Exchange> exchanges = createExchanges();

        Exchange nonDurableExchange = exchanges.get(nonDurableExchangeName);
        Exchange directExchange = exchanges.get(directExchangeName);
        Exchange topicExchange = exchanges.get(topicExchangeName);

        bindAllQueuesToExchange(nonDurableExchange, directRouting);
        bindAllQueuesToExchange(directExchange, directRouting);
        bindAllTopicQueuesToExchange(topicExchange, topicRouting);

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 3, getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        validateExchanges(origExchangeCount, exchanges);

        validateBindingProperties();
    }

    /**
     * Tests binding removal by creating a durable exchange, and queue, binding them together,
     * recovering to verify the persistence, then removing it from the store, and ensuring
     * that following the second reload process it is not recovered.
     */
    public void testDurableBindingRemoval() throws Exception
    {
        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        //create durable queue and exchange, bind them
        Exchange exch = createExchange(DirectExchange.TYPE, directExchangeName, true);
        createQueue(durableQueueName, false, true, false, false);
        bindQueueToExchange(exch, directRouting, queueRegistry.getQueue(durableQueueName), false, null);

        assertEquals("Incorrect number of bindings registered before recovery",
                1, queueRegistry.getQueue(durableQueueName).getBindings().size());

        //verify binding is actually normally recovered
        reloadVirtualHost();

        queueRegistry = getVirtualHost().getQueueRegistry();
        assertEquals("Incorrect number of bindings registered after first recovery",
                1, queueRegistry.getQueue(durableQueueName).getBindings().size());

        exch = getVirtualHost().getExchange(directExchangeName);
        assertNotNull("Exchange was not recovered", exch);

        //remove the binding and verify result after recovery
        unbindQueueFromExchange(exch, directRouting, queueRegistry.getQueue(durableQueueName), false, null);

        reloadVirtualHost();

        queueRegistry = getVirtualHost().getQueueRegistry();
        assertEquals("Incorrect number of bindings registered after second recovery",
                0, queueRegistry.getQueue(durableQueueName).getBindings().size());
    }

    /**
     * Validates that the durable exchanges are still present, the non durable exchange is not,
     * and that the new exchanges are not the same objects as the provided list (i.e. that the
     * reload actually generated new exchange objects)
     */
    private void validateExchanges(int originalNumExchanges, Map<String, Exchange> oldExchanges)
    {
        Collection<Exchange> exchanges = getVirtualHost().getExchanges();
        Collection<String> exchangeNames = new ArrayList(exchanges.size());
        for(Exchange exchange : exchanges)
        {
            exchangeNames.add(exchange.getName());
        }
        assertTrue(directExchangeName + " exchange NOT reloaded",
                exchangeNames.contains(directExchangeName));
        assertTrue(topicExchangeName + " exchange NOT reloaded",
                exchangeNames.contains(topicExchangeName));
        assertTrue(nonDurableExchangeName + " exchange reloaded",
                !exchangeNames.contains(nonDurableExchangeName));

        //check the old exchange objects are not the same as the new exchanges
        assertTrue(directExchangeName + " exchange NOT reloaded",
                getVirtualHost().getExchange(directExchangeName) != oldExchanges.get(directExchangeName));
        assertTrue(topicExchangeName + " exchange NOT reloaded",
                getVirtualHost().getExchange(topicExchangeName) != oldExchanges.get(topicExchangeName));

        // There should only be the original exchanges + our 2 recovered durable exchanges
        assertEquals("Incorrect number of exchanges available",
                originalNumExchanges + 2, getVirtualHost().getExchanges().size());
    }

    /** Validates the Durable queues and their properties are as expected following recovery */
    private void validateBindingProperties()
    {
        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        assertEquals("Incorrect number of (durable) queues following recovery", 6, queueRegistry.getQueues().size());

        validateBindingProperties(queueRegistry.getQueue(durablePriorityQueueName).getBindings(), false);
        validateBindingProperties(queueRegistry.getQueue(durablePriorityTopicQueueName).getBindings(), true);
        validateBindingProperties(queueRegistry.getQueue(durableQueueName).getBindings(), false);
        validateBindingProperties(queueRegistry.getQueue(durableTopicQueueName).getBindings(), true);
        validateBindingProperties(queueRegistry.getQueue(durableExclusiveQueueName).getBindings(), false);
    }

    /**
     * Validate that each queue is bound only once following recovery (i.e. that bindings for non durable
     * queues or to non durable exchanges are not recovered), and if a selector should be present
     * that it is and contains the correct value
     *
     * @param bindings     the set of bindings to validate
     * @param useSelectors if set, check the binding has a JMS_SELECTOR argument and the correct value for it
     */
    private void validateBindingProperties(List<Binding> bindings, boolean useSelectors)
    {
        assertEquals("Each queue should only be bound once.", 1, bindings.size());

        Binding binding = bindings.get(0);

        if (useSelectors)
        {
            assertTrue("Binding does not contain a Selector argument.",
                    binding.getArguments().containsKey(AMQPFilterTypes.JMS_SELECTOR.toString()));
            assertEquals("The binding selector argument is incorrect", SELECTOR_VALUE,
                    binding.getArguments().get(AMQPFilterTypes.JMS_SELECTOR.toString()).toString());
        }
    }

    private void setQueueExclusivity(boolean exclusive) throws AMQException
    {
        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        AMQQueue queue = queueRegistry.getQueue(durableExclusiveQueueName);

        queue.setExclusive(exclusive);
    }

    private void validateQueueExclusivityProperty(boolean expected)
    {
        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        AMQQueue queue = queueRegistry.getQueue(durableExclusiveQueueName);

        assertEquals("Queue exclusivity was incorrect", queue.isExclusive(), expected);
    }


    private void validateDurableQueueProperties()
    {
        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        validateQueueProperties(queueRegistry.getQueue(durablePriorityQueueName), true, true, false, false);
        validateQueueProperties(queueRegistry.getQueue(durablePriorityTopicQueueName), true, true, false, false);
        validateQueueProperties(queueRegistry.getQueue(durableQueueName), false, true, false, false);
        validateQueueProperties(queueRegistry.getQueue(durableTopicQueueName), false, true, false, false);
        validateQueueProperties(queueRegistry.getQueue(durableExclusiveQueueName), false, true, true, false);
        validateQueueProperties(queueRegistry.getQueue(durableLastValueQueueName), false, true, true, true);
    }

    private void validateQueueProperties(AMQQueue queue, boolean usePriority, boolean durable, boolean exclusive, boolean lastValueQueue)
    {
        if(usePriority || lastValueQueue)
        {
            assertNotSame("Queues cant be both Priority and LastValue based", usePriority, lastValueQueue);
        }

        if (usePriority)
        {
            assertEquals("Queue is no longer a Priority Queue", AMQPriorityQueue.class, queue.getClass());
            assertEquals("Priority Queue does not have set priorities",
                    DEFAULT_PRIORTY_LEVEL, ((AMQPriorityQueue) queue).getPriorities());
        }
        else if (lastValueQueue)
        {
            assertEquals("Queue is no longer a LastValue Queue", ConflationQueue.class, queue.getClass());
            assertEquals("LastValue Queue Key has changed", LVQ_KEY, ((ConflationQueue) queue).getConflationKey());
        }
        else
        {
            assertEquals("Queue is not 'simple'", SimpleAMQQueue.class, queue.getClass());
        }

        assertEquals("Queue owner is not as expected", queueOwner, queue.getOwner());
        assertEquals("Queue durability is not as expected", durable, queue.isDurable());
        assertEquals("Queue exclusivity is not as expected", exclusive, queue.isExclusive());
    }

    /**
     * Delete the Store Environment path
     *
     * @param environmentPath The configuration that contains the store environment path.
     */
    private void cleanup(File environmentPath)
    {
        if (environmentPath.exists())
        {
            FileUtils.delete(environmentPath, true);
        }
    }

    private void sendMessageOnExchange(Exchange exchange, AMQShortString routingKey, boolean deliveryMode)
    {
        //Set MessagePersistence
        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        properties.setDeliveryMode(deliveryMode ? Integer.valueOf(2).byteValue() : Integer.valueOf(1).byteValue());
        FieldTable headers = properties.getHeaders();
        headers.setString("Test", "MST");
        properties.setHeaders(headers);

        MessagePublishInfo messageInfo = new TestMessagePublishInfo(exchange, false, false, routingKey);

        final IncomingMessage currentMessage;


        currentMessage = new IncomingMessage(messageInfo);

        currentMessage.setExchange(exchange);

        ContentHeaderBody headerBody = new ContentHeaderBody(BasicConsumeBodyImpl.CLASS_ID,0,properties,0l);

        try
        {
            currentMessage.setContentHeaderBody(headerBody);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }

        currentMessage.setExpiration();

        MessageMetaData mmd = currentMessage.headersReceived(System.currentTimeMillis());
        currentMessage.setStoredMessage(getVirtualHost().getMessageStore().addMessage(mmd));
        currentMessage.getStoredMessage().flushToStore();
        currentMessage.route();


        // check and deliver if header says body length is zero
        if (currentMessage.allContentReceived())
        {
            ServerTransaction trans = new AutoCommitTransaction(getVirtualHost().getMessageStore());
            final List<? extends BaseQueue> destinationQueues = currentMessage.getDestinationQueues();
            trans.enqueue(currentMessage.getDestinationQueues(), currentMessage, new ServerTransaction.Action() {
                public void postCommit()
                {
                    try
                    {
                        AMQMessage message = new AMQMessage(currentMessage.getStoredMessage());

                        for(BaseQueue queue : destinationQueues)
                        {
                            queue.enqueue(message);
                        }
                    }
                    catch (AMQException e)
                    {
                        e.printStackTrace();
                    }
                }

                public void onRollback()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });
        }
    }

    private void createAllQueues()
    {
        //Register Durable Priority Queue
        createQueue(durablePriorityQueueName, true, true, false, false);

        //Register Durable Simple Queue
        createQueue(durableQueueName, false, true, false, false);

        //Register Durable Exclusive Simple Queue
        createQueue(durableExclusiveQueueName, false, true, true, false);

        //Register Durable LastValue Queue
        createQueue(durableLastValueQueueName, false, true, true, true);

        //Register NON-Durable Priority Queue
        createQueue(priorityQueueName, true, false, false, false);

        //Register NON-Durable Simple Queue
        createQueue(queueName, false, false, false, false);
    }

    private void createAllTopicQueues()
    {
        //Register Durable Priority Queue
        createQueue(durablePriorityTopicQueueName, true, true, false, false);

        //Register Durable Simple Queue
        createQueue(durableTopicQueueName, false, true, false, false);

        //Register NON-Durable Priority Queue
        createQueue(priorityTopicQueueName, true, false, false, false);

        //Register NON-Durable Simple Queue
        createQueue(topicQueueName, false, false, false, false);
    }

    private void createQueue(AMQShortString queueName, boolean usePriority, boolean durable, boolean exclusive, boolean lastValueQueue)
    {

        FieldTable queueArguments = null;

        if(usePriority || lastValueQueue)
        {
            assertNotSame("Queues cant be both Priority and LastValue based", usePriority, lastValueQueue);
        }

        if (usePriority)
        {
            queueArguments = new FieldTable();
            queueArguments.put(new AMQShortString(AMQQueueFactory.X_QPID_PRIORITIES), DEFAULT_PRIORTY_LEVEL);
        }

        if (lastValueQueue)
        {
            queueArguments = new FieldTable();
            queueArguments.put(new AMQShortString(AMQQueueFactory.QPID_LAST_VALUE_QUEUE_KEY), LVQ_KEY);
        }

        AMQQueue queue = null;

        //Ideally we would be able to use the QueueDeclareHandler here.
        try
        {
            queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName.asString(), durable, queueOwner.asString(), false, exclusive,
                    getVirtualHost(), FieldTable.convertToMap(queueArguments));

            validateQueueProperties(queue, usePriority, durable, exclusive, lastValueQueue);

            if (queue.isDurable() && !queue.isAutoDelete())
            {
                DurableConfigurationStoreHelper.createQueue(getVirtualHost().getDurableConfigurationStore(),
                        queue,
                        queueArguments);
            }
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }

        getVirtualHost().getQueueRegistry().registerQueue(queue);

    }

    private Map<String, Exchange> createExchanges()
    {
        Map<String, Exchange> exchanges = new HashMap<String, Exchange>();

        //Register non-durable DirectExchange
        exchanges.put(nonDurableExchangeName, createExchange(DirectExchange.TYPE, nonDurableExchangeName, false));

        //Register durable DirectExchange and TopicExchange
        exchanges.put(directExchangeName ,createExchange(DirectExchange.TYPE, directExchangeName, true));
        exchanges.put(topicExchangeName,createExchange(TopicExchange.TYPE, topicExchangeName, true));

        return exchanges;
    }

    private Exchange createExchange(ExchangeType<?> type, String name, boolean durable)
    {
        Exchange exchange = null;

        try
        {
            exchange = getVirtualHost().createExchange(null, name, type.getName().toString(), durable, false, null);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }

        return exchange;
    }

    private void bindAllQueuesToExchange(Exchange exchange, AMQShortString routingKey)
    {
        FieldTable queueArguments = new FieldTable();
        queueArguments.put(new AMQShortString(AMQQueueFactory.X_QPID_PRIORITIES), DEFAULT_PRIORTY_LEVEL);

        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(durablePriorityQueueName), false, queueArguments);
        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(durableQueueName), false, null);
        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(priorityQueueName), false, queueArguments);
        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(queueName), false, null);
        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(durableExclusiveQueueName), false, null);
    }

    private void bindAllTopicQueuesToExchange(Exchange exchange, AMQShortString routingKey)
    {
        FieldTable queueArguments = new FieldTable();
        queueArguments.put(new AMQShortString(AMQQueueFactory.X_QPID_PRIORITIES), DEFAULT_PRIORTY_LEVEL);

        QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(durablePriorityTopicQueueName), true, queueArguments);
        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(durableTopicQueueName), true, null);
        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(priorityTopicQueueName), true, queueArguments);
        bindQueueToExchange(exchange, routingKey, queueRegistry.getQueue(topicQueueName), true, null);
    }


    protected void bindQueueToExchange(Exchange exchange, AMQShortString routingKey, AMQQueue queue, boolean useSelector, FieldTable queueArguments)
    {
        FieldTable bindArguments = null;

        if (useSelector)
        {
            bindArguments = new FieldTable();
            bindArguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue(), SELECTOR_VALUE );
        }

        try
        {
            exchange.addBinding(String.valueOf(routingKey), queue, FieldTable.convertToMap(bindArguments));
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    protected void unbindQueueFromExchange(Exchange exchange, AMQShortString routingKey, AMQQueue queue, boolean useSelector, FieldTable queueArguments)
    {
        FieldTable bindArguments = null;

        if (useSelector)
        {
            bindArguments = new FieldTable();
            bindArguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue(), SELECTOR_VALUE );
        }

        try
        {
            exchange.removeBinding(String.valueOf(routingKey), queue, FieldTable.convertToMap(bindArguments));
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    private void validateMessageOnTopics(long messageCount, boolean allQueues)
    {
        validateMessageOnQueue(durablePriorityTopicQueueName, messageCount);
        validateMessageOnQueue(durableTopicQueueName, messageCount);

        if (allQueues)
        {
            validateMessageOnQueue(priorityTopicQueueName, messageCount);
            validateMessageOnQueue(topicQueueName, messageCount);
        }
    }

    private void validateMessageOnQueues(long messageCount, boolean allQueues)
    {
        validateMessageOnQueue(durablePriorityQueueName, messageCount);
        validateMessageOnQueue(durableQueueName, messageCount);

        if (allQueues)
        {
            validateMessageOnQueue(priorityQueueName, messageCount);
            validateMessageOnQueue(queueName, messageCount);
        }
    }

    private void validateMessageOnQueue(AMQShortString queueName, long messageCount)
    {
        AMQQueue queue = getVirtualHost().getQueueRegistry().getQueue(queueName);

        assertNotNull("Queue(" + queueName + ") not correctly registered:", queue);

        assertEquals("Incorrect Message count on queue:" + queueName, messageCount, queue.getMessageCount());
    }

    private class TestMessagePublishInfo implements MessagePublishInfo
    {

        Exchange _exchange;
        boolean _immediate;
        boolean _mandatory;
        AMQShortString _routingKey;

        TestMessagePublishInfo(Exchange exchange, boolean immediate, boolean mandatory, AMQShortString routingKey)
        {
            _exchange = exchange;
            _immediate = immediate;
            _mandatory = mandatory;
            _routingKey = routingKey;
        }

        public AMQShortString getExchange()
        {
            return _exchange.getNameShortString();
        }

        public void setExchange(AMQShortString exchange)
        {
            //no-op
        }

        public boolean isImmediate()
        {
            return _immediate;
        }

        public boolean isMandatory()
        {
            return _mandatory;
        }

        public AMQShortString getRoutingKey()
        {
            return _routingKey;
        }
    }
}
