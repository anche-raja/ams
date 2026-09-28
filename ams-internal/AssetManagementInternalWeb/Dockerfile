# ------------------------------------------------------------------------------
# AMS Internal Asset Management
#
# Deploys the WAR directly rather than the EAR. The EAR module is still built - it is how the
# application was installed into a shared application server before this - but a container runs one
# application, so the extra packaging layer buys nothing here.
# ------------------------------------------------------------------------------
# Migrated from WebSphere Liberty to Apache Tomcat 10.1. Tomcat is a servlet container: it provides
# JNDI and a per-Resource connection pool, but no JTA, no CDI and no Liberty features. Everything
# the old server.xml supplied now comes from META-INF/context.xml, from Tomcat's lib/, or is flagged.
#
# The original image targeted Java 8 (maven.compiler.target=1.8, jdbc-4.1, ojdbc8). Java 8 is not
# available on Tomcat 10.1 base images; the jdk17 variant is the lowest supported. See manual_flags
# for the JDK bump and its implications for the JDBC driver choice.
FROM tomcat:10.1-jdk17-temurin

USER root

# ------------------------------------------------------------------------------
# Oracle JDBC driver.
#
# Tomcat's connection pool loads the driver from $CATALINA_BASE/lib, not from the WAR. Copied here
# as a file, matching the Oracle 23ai server it talks to. See manual_flags for the Maven coordinate
# and the driver-vs-JDK note.
# ------------------------------------------------------------------------------
COPY lib/ojdbc8-23.8.0.25.04.jar /usr/local/tomcat/lib/

# ------------------------------------------------------------------------------
# Internal certificate authority.
#
# The reverse proxy and the platform REST services present certificates signed by an internal CA,
# which no public trust store knows about. Importing it into the JVM's cacerts is what lets the
# outbound calls complete; without it every one fails the TLS handshake.
# ------------------------------------------------------------------------------
COPY certs/internal-ca.crt /tmp/internal-ca.crt
# The keystore path is detected rather than hard-coded across JDK layouts.
RUN set -eux; \
    CACERTS="$JAVA_HOME/lib/security/cacerts"; \
    [ -f "$CACERTS" ] || CACERTS="$JAVA_HOME/jre/lib/security/cacerts"; \
    [ -f "$CACERTS" ] || { echo "no cacerts under $JAVA_HOME"; exit 1; }; \
    keytool -importcert -noprompt -trustcacerts \
      -alias ams-internal-ca \
      -file /tmp/internal-ca.crt \
      -keystore "$CACERTS" \
      -storepass changeit; \
    rm -f /tmp/internal-ca.crt

# ------------------------------------------------------------------------------
# The application. Copied last: it is the layer that changes on every build, so everything above
# it stays cached. The WAR file name preserves the old context root /AssetManagementInternalWeb.
# ------------------------------------------------------------------------------
COPY target/AssetManagementInternalWeb.war /usr/local/tomcat/webapps/AssetManagementInternalWeb.war

EXPOSE 8080

# The health endpoint is permitted without authentication precisely so this can reach it.
#
# Written against bash's /dev/tcp rather than curl: the base image ships neither curl nor wget, so
# the obvious version of this check fails with "curl: not found".
#
# Port changed from 9081 (Liberty) to 8080 (Tomcat default). start-period kept generous.
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && \
      printf "GET /AssetManagementInternalWeb/health HTTP/1.0\r\n\r\n" >&3 && \
      grep -q "200" <&3' || exit 1
