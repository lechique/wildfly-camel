/**
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
package org.wildfly.camel.test.aws;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.sns.SnsConstants;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.aws.subA.SNSClientProducer;
import org.wildfly.camel.test.aws.subA.SNSClientProducer.SNSClientProvider;
import org.wildfly.camel.test.common.aws.BasicCredentialsProvider;
import org.wildfly.camel.test.common.aws.SNSUtils;
import org.wildfly.extension.camel.CamelAware;
import org.wildfly.extension.camel.WildFlyCamelContext;

import com.amazonaws.services.sns.AmazonSNSClient;

@CamelAware
@RunWith(Arquillian.class)
public class SNSIntegrationTest {

    @Inject
    private SNSClientProvider provider;
    
    @Deployment
    public static JavaArchive deployment() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "aws-sns-tests.jar");
        archive.addClasses(SNSClientProducer.class, SNSUtils.class, BasicCredentialsProvider.class);
        archive.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
        return archive;
    }
    
    @Test
    public void sendInOnly() throws Exception {
        
        AmazonSNSClient snsClient = provider.getClient();
        Assume.assumeNotNull("AWS client not null", snsClient);
        
        WildFlyCamelContext camelctx = new WildFlyCamelContext();
        camelctx.getNamingContext().bind("snsClientA", snsClient);
        
        camelctx.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                .to("aws-sns://" + SNSUtils.TOPIC_NAME + "?amazonSNSClient=#snsClientA");
            }
        });
        
        camelctx.start();
        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Exchange exchange = producer.send("direct:start", ExchangePattern.InOnly, new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.getIn().setHeader(SnsConstants.SUBJECT, "This is my subject");
                    exchange.getIn().setBody("This is my message text.");
                }
            });
            
            Assert.assertNotNull(exchange.getIn().getHeader(SnsConstants.MESSAGE_ID));
            
        } finally {
            camelctx.stop();
        }
    }
    
    @Test
    public void sendInOut() throws Exception {
        
        AmazonSNSClient snsClient = provider.getClient();
        Assume.assumeNotNull("AWS client not null", snsClient);
        
        WildFlyCamelContext camelctx = new WildFlyCamelContext();
        camelctx.getNamingContext().bind("snsClientB", snsClient);
        
        camelctx.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                .to("aws-sns://" + SNSUtils.TOPIC_NAME + "?amazonSNSClient=#snsClientB");
            }
        });
        
        camelctx.start();
        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Exchange exchange = producer.send("direct:start", ExchangePattern.InOut, new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.getIn().setHeader(SnsConstants.SUBJECT, "This is my subject");
                    exchange.getIn().setBody("This is my message text.");
                }
            });
            
            Assert.assertNotNull(exchange.getOut().getHeader(SnsConstants.MESSAGE_ID));
            
        } finally {
            camelctx.stop();
        }
    }
}
