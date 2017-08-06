# R2
An easy way to expose back-end 24x7 services

A set of interfaces to break down the business critical server in modules, 
like microservices, in order to acquire this benefits:

* Modular approach
    * Easy to develop, in a pipeline of microservices
    * Easy to make unitary tests, and to simulate complex situations
    * Easy to detect and diagnose failures
    * Full re-usable modules (no need to compile, just declare to re-use)
* High availability services
    * Modules can be replaced on-line (hot deploy)
    * Continuous service level monitoring by tracking key information
    * Centralized and documented system configuration 
    * On-line monitoring facility, to analize suspect modules (resources leakage)
    * Clustering, HA container friendly
    * Secure and robust (modules that avoid hacking)
* Lightweight
    * Extremely scalable (more than 1M module executions per second)
    * Optional non blocking implementations (asynchronous services)
    * Optional java stand-alone implementation (no container required)
    * Extremely simple to use (just implement a Interface)

This extensible architecture may change the way we develop applications, and maintain it
along de time: One time development, after that just re-use o create a better one. 
When we have a lot of people developing components, and changing people, it's a good 
idea to keep it simple, and never correct a module, just do it better. 

And is a good practice to rule a simple and complete interface to the developer team.
It forces to acquire requirements needed to bring services 24x7, while hiding its complexity.


#### Motivation

Some years ago I was trying to make an easier approach to distributed systems based
on microservices modules, linked in a pipeline and deployed by the container.
This idea incorporates the best of both worlds: 
 - Extreme simplicity, full performance, diagnostics, full re-usable modules in a 
 java standalone application.
 - The powe of the container: High availability, Hot deploy, Multiple versions of the
same objects running together. 
That means real 24x7 services, in business critical application servers.


But it leaves two open questions:

1.  Is possible to bring all that benefits without problems? Or a container using some facility 
can do it better?
2.  And of course, even a little bug in this code may have a serious impact on exposed services. 


This is my invitation to participate in this open source project. 
Gustavo Camargo





