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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

/**
 * Unit test that we can produce JMS message from files
 */
public class FileRouteToJmsTest extends AbstractJMSTest {

    protected String componentName = "activemq";

    @Test
    public void testRouteToFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        deleteDirectory("target/routefromfile");
        template.sendBodyAndHeader("file://target/routefromfile", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file://target/routefromfile").to("activemq:queue:FileRouteToJmsTest");

                from("activemq:queue:FileRouteToJmsTest").to("mock:result");
            }
        };
    }
}
