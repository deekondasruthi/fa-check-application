FROM openjdk:17

COPY middleware_system-0.0.1-SNAPSHOT.jar middleware_system-0.0.1-SNAPSHOT.jar

RUN mv middleware_system-0.0.1-SNAPSHOT.jar ROOT.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "ROOT.jar"]
