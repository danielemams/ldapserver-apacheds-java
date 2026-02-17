FROM registry.redhat.io/ubi9/openjdk-21:1.24-2 AS build
USER root
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM registry.redhat.io/ubi9/openjdk-21-runtime:1.24-2
WORKDIR /app
COPY --from=build /app/target/apacheds-1.0.0-SNAPSHOT-jar-with-dependencies.jar ./app.jar
COPY ./file.ldif ./file.ldif
ARG DEFAULT_PORT=10389
ENV LDAP_PORT=${DEFAULT_PORT}
EXPOSE ${LDAP_PORT}
ENTRYPOINT ["java", "-jar", "app.jar", "./file.ldif"]