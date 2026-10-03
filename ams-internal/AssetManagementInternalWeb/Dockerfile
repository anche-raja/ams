# ------------------------------------------------------------------------------
# Asset Management System
#
# Deploys the WAR directly rather than the EAR. The EAR module is still built - it is how the
# application was installed into a shared application server before this - but a container runs one
# application, so the extra packaging layer buys nothing here.
# ------------------------------------------------------------------------------
# Tomcat 9 (Servlet 4.0 / JSP 2.3, the javax.* namespace the application is written against) on
# Temurin JDK 8, the JDK the application is built for (maven.compiler.source/target 1.8). The WAR is
# Java 8 bytecode and also runs on newer JDKs - the local jdk21 instance proves it - so the base
# image can move to a newer JDK without a rebuild. The image has an arm64 build, so nothing runs
# emulated on Apple Silicon.
FROM tomcat:9.0-jdk8-temurin

USER root

# ------------------------------------------------------------------------------
# Oracle JDBC driver.
#
# Loaded by Tomcat's own connection pool from $CATALINA_BASE/lib - the pool is declared in the
# WAR's META-INF/context.xml and lives in the server's class loader, which cannot see WEB-INF/lib.
# Copied in as a file rather than resolved from Maven Central, matching the ojdbc8-23.8.0.25.04
# runtime driver that talks to the Oracle 23ai server.
# ------------------------------------------------------------------------------
COPY lib/ojdbc8-23.8.0.25.04.jar /usr/local/tomcat/lib/

# ------------------------------------------------------------------------------
# Let context.xml read its connection values from the environment.
#
# Tomcat substitutes ${name:-default} in its XML from system properties only, unless this property
# source is registered. With it, the AMS_DATASOURCE_* variables docker-compose.yml exports are
# what the pool is built from; without it the WAR would silently start on its embedded H2 default.
# ------------------------------------------------------------------------------
RUN echo 'org.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource' \
      >> /usr/local/tomcat/conf/catalina.properties

# ------------------------------------------------------------------------------
# Internal certificate authority.
#
# The reverse proxy and the platform REST services present certificates signed by an internal CA,
# which no public trust store knows about. Importing it into the JVM's cacerts is what lets the
# outbound calls complete; without it every one fails the TLS handshake.
# ------------------------------------------------------------------------------
COPY certs/internal-ca.crt /tmp/internal-ca.crt
# The keystore path is detected rather than hard-coded. On a JDK 8 layout cacerts sits at
# $JAVA_HOME/jre/lib/security/cacerts; the fallback covers JDK 9+ layouts should the base image move.
RUN set -eux; \
    CACERTS="$JAVA_HOME/jre/lib/security/cacerts"; \
    [ -f "$CACERTS" ] || CACERTS="$JAVA_HOME/lib/security/cacerts"; \
    [ -f "$CACERTS" ] || { echo "no cacerts under $JAVA_HOME"; exit 1; }; \
    keytool -importcert -noprompt -trustcacerts \
      -alias ams-internal-ca \
      -file /tmp/internal-ca.crt \
      -keystore "$CACERTS" \
      -storepass changeit; \
    rm -f /tmp/internal-ca.crt

RUN mkdir -p /logs && chmod -R g+rw /logs

# ------------------------------------------------------------------------------
# The application. Copied last: it is the layer that changes on every build, so everything above
# it stays cached. The WAR name is kept as AssetManagementInternalWeb.war so Tomcat derives the
# context root /AssetManagementInternalWeb - the same context root the application has always had.
# ------------------------------------------------------------------------------
COPY target/AssetManagementInternalWeb.war /usr/local/tomcat/webapps/AssetManagementInternalWeb.war

# Tomcat listens on 8080 (HTTP). TLS is terminated at the reverse proxy; configure an HTTPS
# connector in conf/server.xml if it ever has to be terminated in-container instead.
EXPOSE 8080

# The health endpoint is permitted without authentication precisely so this can reach it.
#
# Written against bash's /dev/tcp rather than curl: this image ships neither curl nor wget, so the
# obvious version of this check fails with "curl: not found" and reports the container unhealthy
# forever while the application is running perfectly.
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && \
      printf "GET /AssetManagementInternalWeb/health HTTP/1.0\r\n\r\n" >&3 && \
      grep -q "200" <&3' || exit 1
