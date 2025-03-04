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
package org.apache.camel.component.jms.issues;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsPassThroughtJmsKeyFormatStrategyEndpointTest extends AbstractJMSTest {

    private final String uri
            = "activemq:queue:JmsPassThroughtJmsKeyFormatStrategyEndpointTest?jmsKeyFormatStrategy=passthrough";

    @Test
    public void testSendWithHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo("Hello World");
        mock.message(0).header("HEADER_1").isEqualTo("VALUE_1");
        mock.message(0).header("HEADER_2").isEqualTo("VALUE_2");

        template.sendBodyAndHeader(uri, "Hello World", "HEADER_1", "VALUE_1");

        assertMockEndpointsSatisfied();

        assertEquals("VALUE_1", mock.getReceivedExchanges().get(0).getIn().getHeader("HEADER_1"));
        assertEquals("VALUE_2", mock.getReceivedExchanges().get(0).getIn().getHeader("HEADER_2"));

        assertEquals("VALUE_1", mock.getReceivedExchanges().get(0).getIn().getHeaders().get("HEADER_1"));
        assertEquals("VALUE_2", mock.getReceivedExchanges().get(0).getIn().getHeaders().get("HEADER_2"));
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri)
                        .process(exchange -> {
                            Map<String, Object> headers = exchange.getIn().getHeaders();
                            assertEquals("VALUE_1", headers.get("HEADER_1"));
                            assertEquals("VALUE_1", exchange.getIn().getHeader("HEADER_1"));
                        })
                        .setHeader("HEADER_2", constant("VALUE_2"))
                        .to("mock:result");
            }
        };
    }
}
