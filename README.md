[![Build Status](https://travis-ci.org/SolaceSamples/solace-samples-jms.svg?branch=master)](https://travis-ci.org/SolaceSamples/solace-samples-jms)

# Demo Based on Solace Getting Started Tutorial
## Solace JMS API and Messaging System Required

This demo requires a running Solace messaging system to connect with.
There are two ways you can get started:

- Follow [these instructions](https://cloud.solace.com/learn/group_getting_started/ggs_signup.html) to quickly spin up a cloud-based Solace messaging service for your applications.
- Follow [these instructions](https://docs.solace.com/Solace-SW-Broker-Set-Up/Setting-Up-SW-Brokers.htm) to start the Solace VMR in leading Clouds, Container Platforms or Hypervisors. The tutorials outline where to download and how to install the Solace VMR.

The project includes the latest version of the Solace JMS API implementation at time of creation.

## Build the demo

Just clone and build. For example:

  1. clone this GitHub repository
  2. `./gradlew assemble`

## Run the Demo

This demo is for exploring integration and performance of the Solace Java APIs.

Scripts:

    ./build/staged/bin/jmsT1Publisher <host:port> <message-vpn> <client-username> [password]
    ./build/staged/bin/jmsT1Q1Consumer <host:port> <message-vpn> <client-username> [password]
    ./build/staged/bin/jmsT1Q2_T2Processor <host:port> <message-vpn> <client-username> [password]
    ./build/staged/bin/jmsT2Q3Consumer <host:port> <message-vpn> <client-username> [password]

    ./build/staged/bin/jcsmpT1Publisher <host:port> <message-vpn> <client-username> [password]
    ./build/staged/bin/jcsmpT1Q1Consumer <host:port> <message-vpn> <client-username> [password]
    ./build/staged/bin/jcsmpT1Q2_T2Processor <host:port> <message-vpn> <client-username> [password]
    ./build/staged/bin/jcsmpT2Q3Consumer <host:port> <message-vpn> <client-username> [password]

Tip - You can find above launch parameters in Connect tab of the Cluster Manager page (expand first section)
   
(description details to be added)


## Authors

This demo is being prepared by Karl Ossoinig, and is based on work done in the Java related solace-samples projects.

## License

This project is licensed under the Apache License, Version 2.0. - See the [LICENSE](LICENSE) file for details.

## Resources

For more information try these resources:

- The Solace Developer Portal website at: https://solace.dev
- Ask the https://solace.community
- Solace API Tutorials @ https://tutorials.solace.dev
