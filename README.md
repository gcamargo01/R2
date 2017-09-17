# R2
An easy way to expose back-end 24x7 services

A set of interfaces to break down the business critical server in modules, 
like micro-services, in order to acquire this benefits:

* Modular approach
    * Easy to develop, in a pipeline of microservices
    * Easy to make unitary tests, and to simulate complex situations
    * Easy to detect and diagnose failures
    * Full re-usable modules (no need to compile, just declare to re-use)
* High availability services
    * Modules can be replaced on-line (hot deploy)
    * Continuous service level monitoring by tracking key information
    * Centralized and documented system configuration 
    * It easily monitors and analizes suspicious modules of malfuncions
    * Clustering, HA container friendly
    * Secure and robust (modules that avoid hacking)
* Lightweight
    * Extremely scalable (more than 1M module executions per second)
    * Optional non blocking implementations (asynchronous services)
    * Optional java stand-alone implementation (no container required)
    * Extremely simple to use (just implement a Interface)

This extensible arquitecture not only may change the way we develop applications but also the way
they are maintained throughout time. One time development, after that just re-use o create a better 
one. If the working personal in charge of devloping components is in constant change, it is better 
to keep it simple and always create a new module, better that previous ones.

And is a good practice to rule a simple and complete interface to the developer team.
It forces to acquire requirements needed to bring services 24x7, while hiding its complexity.


#### Motivation

Some years ago I was trying to make an easier approach to distributed systems based
on microservices modules, linked in a pipeline and deployed by the container.
This idea incorporates the best of both worlds: 
 - Extremely simplicity, full performance, diagnostics, full re-usable modules in a 
 java standalone application.
 - The powe of the container: High availability, Hot deploy, Multiple versions of the
same objects running together. 
That means real 24x7 services, in business critical application servers.


But it leaves two open questions:

1. Is it possible to obtain all the benefits without getting obstacles in our way? Or is it a better idea 
to use a container with POJOs (JBOSS, Spring, Camel)?

2.  And of course, this project requires a proof of concept, and a lot of work in order to reach the 
required level of reliability to be used in the real world.


This is my invitation to participate in this open source project. 
Gustavo Camargo





