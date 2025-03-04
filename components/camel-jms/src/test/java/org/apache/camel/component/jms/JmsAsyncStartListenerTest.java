/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Testing with async start listener
 */
@Timeout(60)
public class JmsAsyncStartListenerTest extends AbstractPersistentJMSTest {

    protected String componentName = "activemq";

    @Test
    public void testAsyncStartListener() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);

        template.sendBody("activemq:queue:JmsAsyncStartListenerTest", "Hello World");
        template.sendBody("activemq:queue:JmsAsyncStartListenerTest", "Gooday World");

        result.assertIsSatisfied();
    }

    @Override
    protected void createConnectionFactory(CamelContext camelContext) {
        // use a persistent queue as the consumer is started asynchronously
        // so we need a persistent store in case no active consumers when we send the messages
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service);
        JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
        jms.getConfiguration().setAsyncStartListener(true);
        camelContext.addComponent(componentName, jms);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:JmsAsyncStartListenerTest").to("mock:result");
            }
        };
    }
}
