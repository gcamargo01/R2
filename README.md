# R2
An easy way to expose back-end 24x7 services

A set of interfaces to break down the business critical server in modules, 
like microservices, reaching this benefits:

* Modular approach
    * Easy to develop, in a pipeline of microservices
    * Easy to make unitary tests, and easy to simulate complex situations
    * Easy to detect and diagnose failures
    * Full re-usable modules (no need to compile, just declare to re-use)
* High availability services
    * Modules can be replaced on-line (hot deploy)
    * Continuios service level monitoring by tracking key information
    * Centralized and documented system configuration 
    * On-line monitoring facility, to analize suspect modules (resources leakage)
    * Clustering, HA container frendly
    * Secure and robust (modules that avoid hacking)
* Lightweight
    * Extremly scalable (more than 1M module executions per second)
    * Optional non blocking implementations (asyncronous services)
    * Optional java stand-alone implementation (no container required)
    * Extemly simple to use (just implement a Interface)

This extensible architecture may change the way we develop applications, and mantaing it
along de time: One time development, after that just re-use o create a better one. 
When we have a lot of pepole developing components, and changing pepople, it's a good 
idea to keep it simple, and never correct a module, just do it better. 

And its a good practice to rule a simple and complete interface to the developer team.
It forces to acquire requieriments needed to bring services 24x7, while hidding its compexity.


#### Motivation

Some years ago I was tryng to make an easier aproach to distributed systems based
on microservices modules, linked in a pipeline and deployed by the container.
That idea has de best of two worlds: 
 - Extreme simplicity, full performance, diagnostics, full re-usable modules and 
 - The powe of de container: High avaliability, Hot deploy, Multiple versions of the
same objects runing together. 
That means real 24x7 services, in businnes critical aplication servers.


But it have two open questions:

1.  Is possible to bring all that benefits without problems? Or a container using some facility 
can do it better?
2.  And of course, even a little bug in this code may have a serious impact on exposed services. 


So, this is my invitation to participate in this open source project. 


Gustavo Camargo





