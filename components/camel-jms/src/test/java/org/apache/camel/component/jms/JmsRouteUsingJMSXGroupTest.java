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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsConstants.JMS_X_GROUP_ID;
import static org.apache.camel.test.junit5.TestSupport.body;

public class JmsRouteUsingJMSXGroupTest extends AbstractJMSTest {

    @Test
    public void testNoConcurrentProducersJMSXGroupID() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducersJMSXGroupID() throws Exception {
        doSendMessages(10, 1);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files * 2);
        getMockEndpoint("mock:result").expectsNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(() -> {
                template.sendBodyAndHeader("direct:start", "IBM: " + index, JMS_X_GROUP_ID, "IBM");
                template.sendBodyAndHeader("direct:start", "SUN: " + index, JMS_X_GROUP_ID, "SUN");

                return null;
            });
        }

        assertMockEndpointsSatisfied();
        executor.shutdownNow();
    }

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("jms:queue:JmsRouteUsingJMSXGroupTest");

                from("jms:queue:JmsRouteUsingJMSXGroupTest?concurrentConsumers=2").to("log:foo?showHeaders=false")
                        .to("mock:result");
            }
        };
    }

}
