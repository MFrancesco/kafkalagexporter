## Kafka Lag Expoter

##### 1 class and 125 line Spring-Boot application to read and export Apache Kafka Lag as a metric

Apache Kafka Lag is a particular metric that tell us how much lag for a specific topic a consumer group have.

This particular implementation will expose the lag as a [micrometer gauge](https://micrometer.io/docs/concepts#_gauges) 
to be consumed by [prometheus](https://prometheus.io/) on the `/actuator/prometheus` endpoint, and can be easily 
changed to suits your preferred monitoring application.


### How to

- Make package  - Compile
- Make docker   - Create the docker

The spring app can be configured by mounting the docker volume `/opt/cfg` where the app configuration files are fetched.

Make sure to customize the application so it can run in your environment.
