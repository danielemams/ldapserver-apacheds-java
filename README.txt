With this project you can run locally an ApacheDS LDAP Server, running on localhost:10389 by default.
You can customize for now:
1. file.ldif to be used to populate LDAP environment.
2. port to be used for the LDAP Server.

Here some useful tool:
### local run:
# build:
mvn clean install -DskipTests

# execute:
java -jar ./target/apacheds-1.0.0-SNAPSHOT-jar-with-dependencies.jar ./file.ldif

### container-image run:
# build the image:
podman build -t ldapserver-apacheds-java .

# run with default ldif:
podman run -d --name ldap-server -p 10389:10389 localhost/ldapserver-apacheds-java:latest
podman run -d --name ldap-server -p 10389:10389 quay.io/rhn_support_dmammare/ldapserver-apacheds-java
# run with custom-ldif:
podman run -d --name ldap-server -p 10389:10389 -v ./file.ldif:/app/file.ldif:Z localhost/ldapserver-apacheds-java:latest
podman run -d --name ldap-server -p 10389:10389 -v ./file.ldif:/app/file.ldif:Z quay.io/rhn_support_dmammare/ldapserver-apacheds-java
# run on a custom-ldap_port:
podman run -d --name ldap-server -e LDAP_PORT=10390 -p 10390:10390 localhost/ldapserver-apacheds-java:latest
podman run -d --name ldap-server -e LDAP_PORT=10390 -p 10390:10390 quay.io/rhn_support_dmammare/ldapserver-apacheds-java