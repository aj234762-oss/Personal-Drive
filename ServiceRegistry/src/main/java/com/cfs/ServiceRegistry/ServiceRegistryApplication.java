package com.cfs.ServiceRegistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/*
--------------------------------    MicroServices   ------------------
Breaking down of a big project into smaller projects

------------    What is Monolithic ?
If we develop all the functionalities in single project then it is called as monolith
architecture bases application

If we will package our application in one single jar/war to deploy into server

As monolith application contains all functionalities, it will become fat jar/war.

                    MONOLITH
                       |
        --------------------------------
        |       |       |       |       |
       User   Order   Payment  Product  Admin
        |       |       |       |       |
        --------------------------------
                       |
                  One JAR/WAR

============    Advantages  =======
1. Simple to develop
2. Everything is available at one place.
3. Configuration required only once.

============= Disadvantages ========
1. Difficult to maintain.
2. Dependencies among the functionalities (tightly coupled)
3. Single point of failure.

Note :- To overcome problem of monolith , microservice architecture came into the market.
1. MicroService is not programming language.
2. MicroService is not a framework.
3. MicroService is not a specification API.
4. MicroService is an Architectural design pattern.
5. MicroServices suggesting to develop application functionalities with loosely coupling
6. In MicroServices architecture we don't develop all functionalities in single project, we will divide project
   functionalities into several REST API spring boot projects.

How Communication happen between different microservices :- Synchronous and Asynchronous communication , async happen through Kafka(A Broker system)
We can do communication in 2 two ways :- one is RestTemplate and if we are using proper environment then we can use FeignClient.

Interview Question :-

Advantage :-
1. Loosely coupling
2. Easy to maintain
3. Faster Development
4. Quick Deployment
5. Faster release
6. Less Downtime
7. Technology independent ( Multiple Language can be used for different services )

Disadvantage :-
1. Lot of configurations Required
2. Hard to Manage

--------------------------    MicroService Architecture    ------------------------
1. We don't have any fixed architecture for microservices.
2. Developer customizes microservices architecture are per their requirement.

                API Gateway
                     |
       --------------------------------
       |          |         |          |
   User Service Order     Payment    Product
                Service    Service    Service
       |          |         |          |
      DB         DB        DB         DB


Component Used
1. Service Registry (Eureka Server)
2. Services(REST API) ----> Spring boot services which we made
3. Communication
4. API Gateway  --- Glu,Kong
5. Admin Server
6. Zipkin Server

============================= Service Registry  ==============================
1. Service registry acts as DB of services available in the project.
2. It provides the details of all services which are registered with Service registry.
3. We can identify how many services available in the project.
4. We can identify how many instances available for each service.
5. We can use "eureka server" as Service registry.
6. Eureka server provided by "Spring Cloud Netflix" library.


============================= API GATEWAY  ==============================
1. Api gateway is used to manage our backend API's of the projects
2. Api gateway acts as mediator between end users and backend APIs ( can also be used as Load Balancer).
3. Api gateway can filter logic to decide request processing.
4. Api gateway will contain routing logic (which request should go to which REST API)
5. Api gateway also will be registered with service registry. ( This is also spring boot application )

Communication in microservices :-
1. Rest Template
2. Feign Client





*/
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication{

	public static void main(String[] args) {
		SpringApplication.run(ServiceRegistryApplication.class, args);
	}

}
