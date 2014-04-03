Demo App for Mobilyzer
=============================

A demo app showing how to use Mobilyzer in your project. See https://github.com/laoyaosniper/Mobilyzer for details

##How to use this app

Just press the "Submit a measurement task button" and a task on this list will be executed. 

You may found a HTTP result is shown shortly after launching this app. That is used to demonstrate that you can use the library immediately after getting the API object.

The order is 1->2-...->9->1...

0. TCP Throughput (downlink)
1. TCP Throughput (uplink)
2. DNS Lookup
3. HTTP Get
4. Ping
5. Traceroute
6. UDP Burst (Downlink)
7. UDP Burst (Uplink)
8. Sequential task (HTTP: www.google.com and DNS Lookup: www.google.com)
9. Parallel task (Traceroute: www.google.com and Ping: www.google.com)

Beside simply display the results on the screen, you can do more analysis withe results. If you want to know more about it, please check the source code in Mobilyzer repository to see the corresponding methods. The way that TCP Throughput get the median throughput value is a good example.
