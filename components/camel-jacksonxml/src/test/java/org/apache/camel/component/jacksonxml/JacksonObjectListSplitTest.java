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
package org.apache.camel.component.jacksonxml;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.body;

public class JacksonObjectListSplitTest extends CamelTestSupport {
    @Test
    public void testJackson() throws InterruptedException {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessagesMatches(body().isInstanceOf(DummyObject.class));

        template.sendBody("direct:start", "<list><pojo dummy=\"value1\"/><pojo dummy=\"value2\"/></list>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // you can specify the pojo class type for unmarshal the jason file
                JacksonXMLDataFormat format = new JacksonXMLDataFormat(DummyObject.class);
                format.useList();
                from("direct:start").unmarshal(format).split(body()).to("mock:result");
            }
        };
    }

    public static class DummyObject {

        private String dummy;

        public DummyObject() {
        }

        public String getDummy() {
            return dummy;
        }

        public void setDummy(String dummy) {
            this.dummy = dummy;
        }
    }

}
