/*
 * Copyright 2021 Solace Corporation. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.solace.demo;

import com.solacesystems.jcsmp.*;
import com.solacesystems.jcsmp.impl.JCSMPXMLMessageProducer;

import java.io.IOException;

/**
 * A Processor is a microservice or application that receives a message, does something with the info,
 * and then sends it on..!  It is both a publisher and a subscriber, but (mainly) publishes data once
 * it has received an input message.
 * This class is meant to be used with DirectPub and DirectSub, intercepting the published messages and
 * sending them on to a different topic.
 */
public class jcsmpT1Q2_T2Processor {

    private static final String SAMPLE_NAME = jcsmpT1Q2_T2Processor.class.getSimpleName();
    private static final String TOPIC = "swa/crew/pay";      // broker defined, output
    private static final String QUEUE = "CrewPayAnalyticsSvcQueue";  // broker defined, input
    private static final String API = "JCSMP";
    
    private static volatile int msgRecvCounter = 0;              // num messages received
    private static volatile int msgSentCounter = 0;              // num messages sent
    private static volatile boolean hasDetectedDiscard = false;  // detected any discards yet?

    private static volatile boolean isShutdown = false;  // are we done yet?

    /** Main method.
     * @param args
     * @throws JCSMPException
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String... args) throws JCSMPException, IOException, InterruptedException {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");

        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1]);     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]);      // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
        }
        properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);  // re-subscribe Direct subs after reconnect
        properties.setProperty(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT, true);  // client will ack each queued message

        JCSMPChannelProperties channelProperties = new JCSMPChannelProperties();
        channelProperties.setReconnectRetries(20);      // recommended settings
        channelProperties.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProperties);

        /*
         * SUPPORTED_MESSAGE_ACK_AUTO means that the received messages on the Flow
         * are implicitly acknowledged on return from the onReceive() of the XMLMessageListener
         * specified in createFlow().
         */
        properties.setProperty(JCSMPProperties.MESSAGE_ACK_MODE, JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);

        // Create a session for interacting with the PubSub+ broker
        final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) {  // could be reconnecting, connection lost, etc.
                System.out.printf("### Received a Session event: %s%n", event);
            }
        });

        session.connect();  // connect to the broker

        session.getMessageConsumer((XMLMessageListener)null);

        // Simple anonymous inner-class for handling publishing events
        final XMLMessageProducer producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
            @Override public void responseReceivedEx(Object key) {
            }

            // can be called for ACL violations, connection loss, and Persistent NACKs
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("### Producer handleErrorEx() callback: %s%n", cause);
                if (cause instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                } else if (cause instanceof JCSMPErrorResponseException) {  // might have some extra info
                    JCSMPErrorResponseException e = (JCSMPErrorResponseException)cause;
                    System.out.println(JCSMPErrorResponseSubcodeEx.getSubcodeAsString(e.getSubcodeEx())
                            + ": " + e.getResponsePhrase());
                    System.out.println(cause);
                }
            }
        });

        // Simple anonymous inner-class for async receiving of messages
        final XMLMessageConsumer consumer = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage inboundMsg) {
                msgRecvCounter++;
                String inboundTopic = inboundMsg.getDestination().getName();
                    TextMessage outboundMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
                    final String upperCaseMessage = inboundTopic.toUpperCase();  // as a silly example of "processing"
                    outboundMsg.setText(upperCaseMessage);
                    if (inboundMsg.getApplicationMessageId() != null) {  // populate for traceability
                        outboundMsg.setApplicationMessageId(inboundMsg.getApplicationMessageId());
                    }
                    try {
                        producer.send(outboundMsg, JCSMPFactory.onlyInstance().createTopic(TOPIC));
                        msgSentCounter++;
                    } catch (JCSMPException e) {  // threw from send(), only thing that is throwing here, but keep looping (unless shutdown?)
                        System.out.printf("### Caught while trying to producer.send(): %s%n",e);
                        if (e instanceof JCSMPTransportException) {  // unrecoverable
                            isShutdown = true;
                        }
                    }
                    finally {
                        inboundMsg.ackMessage();  // not needed per SUPPORTED_MESSAGE_ACK_AUTO property set above???
                    }
            }

            public void onException(JCSMPException e) {
                System.out.printf("Consumer received exception: %s%n", e);
            }
        });


        // setup the queue, flow properties and endpoint properties
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(QUEUE);
        final ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
        flow_prop.setEndpoint(queue);
        flow_prop.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);
        EndpointProperties endpoint_props = new EndpointProperties();
        endpoint_props.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);

        // instantiate a consumer instance of the consumer inner class with flow and endpoint properties defined above
        Consumer cons = session.createFlow(consumer.getMessageListener(), flow_prop, endpoint_props);

        // connect to the broker
        session.connect();

        // start the consumer instance
        cons.start();
        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        while (System.in.available() == 0 && !isShutdown) {
            Thread.sleep(1000);  // wait 1 second
            System.out.printf("%s %s Received msgs/s: %,d Sent msgs/s: %,d%n",API,SAMPLE_NAME,msgRecvCounter, msgSentCounter);  // simple way of calculating message rates
            msgRecvCounter = 0;
            msgSentCounter = 0;
            if (hasDetectedDiscard) {
                System.out.println("*** Egress discard detected *** : "
                        + SAMPLE_NAME + " unable to keep up with full message rate");
                hasDetectedDiscard = false;  // only show the error once per second
            }
        }
        isShutdown = true;
        session.closeSession();  // will also close consumer object
        System.out.println("Main thread quitting.");
    }
}
