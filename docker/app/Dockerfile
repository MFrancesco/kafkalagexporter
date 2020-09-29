FROM openjdk:11-jdk-slim as app

ENV spring_profiles_active=default

WORKDIR /opt

EXPOSE 8080
ADD cfg/ cfg/
##Assuming you copied the file from target to this dir
COPY *.jar app.jar

VOLUME /opt/cfg


ENTRYPOINT ["java","-jar","/opt/app.jar","--spring.config.location=file:/opt/cfg/"]