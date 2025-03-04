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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsSetBodyNullErrorHandlerUseOriginalMessageTest extends AbstractPersistentJMSTest {

    @Test
    public void testSetNull() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();

        template.sendBody("activemq:queue:JmsSetBodyNullErrorHandlerUseOriginalMessageTest", "Hello World");

        assertMockEndpointsSatisfied();

        String body = consumer.receiveBody("activemq:queue:dead", 5000, String.class);
        assertEquals("Hello World", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("activemq:queue:dead").useOriginalMessage());

                from("activemq:queue:JmsSetBodyNullErrorHandlerUseOriginalMessageTest")
                        .to("mock:foo")
                        .process(exchange -> {
                            // an end user may set the message body explicit to null
                            exchange.getIn().setBody(null);
                        })
                        .to("mock:bar")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
