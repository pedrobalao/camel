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
package org.apache.camel.component.file.remote.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Test the ftps component over TLS (explicit) with client authentication
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.FtpsUtil#hasRequiredAlgorithms")
public class FileToFtpsExplicitTLSWithClientAuthIT extends FtpsServerExplicitTLSWithClientAuthTestSupport {

    protected String getFtpUrl() {
        return "ftps://admin@localhost:{{ftp.server.port}}"
               + "/tmp2/camel?password=admin&initialDelay=2000&disableSecureDataChannelDefaults=true"
               + "&securityProtocol=TLSv1.2&implicit=false&ftpClient.keyStore.file=./src/test/resources/server.jks&ftpClient.keyStore.type=JKS"
               + "&ftpClient.keyStore.algorithm=SunX509&ftpClient.keyStore.password=password&ftpClient.keyStore.keyPassword=password&delete=true";
    }

    @Disabled("CAMEL-16784:Disable testFromFileToFtp tests")
    @Test
    public void testFromFileToFtp() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/data?noop=true").log("Got ${file:name}").to(getFtpUrl());

                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}
