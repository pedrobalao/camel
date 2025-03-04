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
package org.apache.camel.swagger.producer;

import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class RestSwaggerGetTest extends CamelTestSupport {

    @BindToRegistry("dummy")
    private DummyRestProducerFactory factory = new DummyRestProducerFactory();

    @Test
    public void testSwaggerGet() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Donald Duck");

        template.sendBodyAndHeader("direct:start", null, "name", "Donald Duck");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("camelhost").producerComponent("dummy");

                from("direct:start").to("rest:get:hello/hi/{name}?apiDoc=hello-api.json").to("mock:result");
            }
        };
    }
}
