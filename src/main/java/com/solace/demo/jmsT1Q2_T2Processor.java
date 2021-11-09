/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.solace.demo;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;

import javax.jms.*;

/**
 * A Processor is a microservice or application that receives a message, does something with the info,
 * and then sends it on..!  It is both a publisher and a subscriber, but (mainly) publishes data once
 * it has received an input message.
 * This class is meant to be used with DirectPub and DirectSub, intercepting the published messages and
 * sending them on to a different topic.
 */
public class jmsT1Q2_T2Processor {

    private static final String SAMPLE_NAME = jmsT1Q2_T2Processor.class.getSimpleName();
    private static final String TOPIC = "swa/crew/pay";      // topic path to pay events
    private static final String QUEUE = "CrewPayAnalyticsSvcQueue";  // queue path to payraw events
    private static final String API = "JMS";

    private static volatile int msgRecvCounter = 0;              // num messages received
    private static volatile int msgSentCounter = 0;              // num messages sent
    private static volatile boolean hasDetectedDiscard = false;  // detected any discards yet?

    private static volatile boolean isShutdown = false;  // are we done yet?



    /** Main method.
     * @param args
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");

        // Programmatically create the connection factory using default settings
        SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
        connectionFactory.setHost(args[0]);          // host:port
        connectionFactory.setVPN(args[1]);           // message-vpn
        connectionFactory.setUsername(args[2]);      // client-username
        if (args.length > 3) {
            connectionFactory.setPassword(args[3]);  // client-password
        }
        connectionFactory.setReconnectRetries(2);       // recommended settings
        connectionFactory.setConnectRetriesPerHost(2);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        connectionFactory.setDirectTransport(false);    // use Direct transport for "non-persistent" messages
        connectionFactory.setXmlPayload(false);         // use the normal payload section for TextMessage
        connectionFactory.setClientID(API+"_"+SAMPLE_NAME);  // change the name, easier to find
        Connection connection = connectionFactory.createConnection();

        // can be called for ACL violations, connection loss
        connection.setExceptionListener(jmsException -> {  // ExceptionListener.onException()
            System.out.println("### Connection ExceptionListener caught this: "+jmsException);
            if (jmsException.getMessage().contains("JCSMPTransportException")) {
                isShutdown = true;  // bail out
            }
        });

        // Create a session for interacting with the PubSub+ broker
        Session session = connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);

        MessageProducer producer = session.createProducer(null);  // do not bind the producer to a specific topic
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);    // use non-persistent (Direct here) as default
        producer.setDisableMessageID(true);                       // don't auto-populate the JMSMessageID
        producer.setDisableMessageTimestamp(true);                // don't set a send timestamp by default

        // Create the queue programmatically and the corresponding router resource
        // will also be created dynamically because DynamicDurables is enabled.
        Queue queue = session.createQueue(QUEUE);        // Create a consumer on the inbound topic in our session
        MessageConsumer consumer = session.createConsumer(queue);
        // Create the topic we will be publishing to
        Topic topic = session.createTopic(TOPIC);

        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message inboundMsg) {
                try {
                    // do not print anything to console... too slow!
                    msgRecvCounter++;
                    TextMessage outboundMsg = session.createTextMessage();
                        outboundMsg.setText(transformRawEvent(inboundMsg.toString()));
                        if (inboundMsg.getJMSMessageID() != null) {
                            outboundMsg.setJMSMessageID(inboundMsg.getJMSMessageID());  // populate for traceability
                        }
                        try {
                            producer.send(topic,outboundMsg);
                            inboundMsg.acknowledge();
                        } catch (JMSException e) {
                            System.out.println("### Caught at producer.send() " + e);

                }
            } catch (JMSException e) {
                    System.out.println("### Caught in onMessage() " + e);
                }
            }

            private String transformRawEvent(String input) {
                // append simulated additional values
                return input + "{calculatedValueContent}";
            }

        });

        connection.start();  // start receiving messages

        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");

        try {
            while (System.in.available() == 0 && !isShutdown) {
                Thread.sleep(1000);  // wait 1 second
                System.out.printf("%s Received msgs/s: %,d%n",API,msgRecvCounter);  // simple way of calculating message rates
                msgRecvCounter = 0;
                if (hasDetectedDiscard) {
                    System.out.println("*** Egress discard detected *** : "
                            + SAMPLE_NAME + " unable to keep up with full message rate");
                    hasDetectedDiscard = false;  // only show the error once per second
                }
            }
        } catch (InterruptedException e) {
            // Thread.sleep() interrupted... probably getting shut down
        }
        System.out.println("********** We are outside the loop");
        isShutdown = true;
        connection.stop();
        System.out.println("********** after connection stop");
//        session.close();
        connection.close();  // could block here for a while.
        System.out.println("Main thread quitting.");
    }
}
