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

import java.io.IOException;

/** This is a more detailed subscriber sample. */
public class jcsmpT2Q3Consumer {

    private static final String SAMPLE_NAME = jcsmpT2Q3Consumer.class.getSimpleName();
    private static final String QUEUE = "analyticsDataPipelineQueue";  // broker defined
    private static final String API = "JCSMP";




    private static volatile int msgRecvCounter = 0;              // num messages received
    private static volatile boolean hasDetectedDiscard = false;  // detected any discards yet?
    private static volatile boolean isShutdown = false;          // are we done yet?

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
        properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);  // subscribe Direct subs after reconnect
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

        final JCSMPSession session;
        session = JCSMPFactory.onlyInstance().createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) {  // could be reconnecting, connection lost, etc.
                System.out.printf("### Received a Session event: %s%n", event);
            }
        });
        session.connect();  // connect to the broker

        session.getMessageConsumer((XMLMessageListener)null);

        final Queue queue = JCSMPFactory.onlyInstance().createQueue(QUEUE);
        final ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
        flow_prop.setEndpoint(queue);
        flow_prop.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);
        EndpointProperties endpoint_props = new EndpointProperties();
        endpoint_props.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);

        Consumer consumer = session.createFlow(new jcsmpT2Q3Consumer.MessageListener(), flow_prop);
        consumer.start();
        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        while (System.in.available() == 0 && !isShutdown) {
            Thread.sleep(1000);  // wait 1 second
            System.out.printf("%s %s Received msgs/s: %,d%n",API,SAMPLE_NAME,msgRecvCounter);  // simple way of calculating message rates
            msgRecvCounter = 0;
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

    // Used by consumer session for async threaded message callback and exception handling
    static class MessageListener implements XMLMessageListener {
        public void onException(JCSMPException exception) {
            exception.printStackTrace();
        }

        public void onReceive(BytesXMLMessage inboundMsg) {
            // do not print anything to console... too slow!
            msgRecvCounter++;
            String messageText = inboundMsg.toString();
            inboundMsg.ackMessage();  // not needed per SUPPORTED_MESSAGE_ACK_AUTO property set above???
        }
    }
}
