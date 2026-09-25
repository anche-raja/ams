# ------------------------------------------------------------------------------
# AMS Internal Asset Management
#
# Deploys the WAR directly rather than the EAR. The EAR module is still built - it is how the
# application was installed into a shared application server before this - but a container runs one
# application, so the extra packaging layer buys nothing here.
# ------------------------------------------------------------------------------
# Tomcat 10.1 (Servlet 6 / Jakarta EE 10). There is no Java 8 Tomcat 10.1 base image - Tomcat
# 10.1 requires Java 11 or newer - so the old java8/OpenJ9 base cannot be carried across. Using
# the Temurin JDK 17 variant; see manual_flags for the JDK upgrade this forces on the build.
FROM tomcat:10.1-jdk17-temurin

USER root

# ------------------------------------------------------------------------------
# Oracle JDBC driver.
#
# The driver is now Tomcat's, loaded from $CATALINA_BASE/lib by Tomcat's own connection pool
# (the pool is configured in META-INF/context.xml). Copied in as a file rather than resolved from
# Maven Central, matching the ojdbc8-23.8.0.25.04 runtime driver that talks to the Oracle 23ai
# server.
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
# The keystore path is detected rather than hard-coded. On the Temurin JDK 17 layout cacerts sits
# at $JAVA_HOME/lib/security/cacerts; the fallback covers a JRE-root layout.
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

RUN mkdir -p /logs && chmod -R g+rw /logs

# ------------------------------------------------------------------------------
# The application. Copied last: it is the layer that changes on every build, so everything above
# it stays cached. The WAR name is kept as AssetManagementInternalWeb.war so Tomcat derives the
# context root /AssetManagementInternalWeb - the same context root the old server used.
# ------------------------------------------------------------------------------
COPY target/AssetManagementInternalWeb.war /usr/local/tomcat/webapps/AssetManagementInternalWeb.war

# Tomcat listens on 8080 (HTTP). The old Liberty ports 9081/9444 are gone; configure TLS in
# Tomcat's conf/server.xml if it is terminated in-container rather than at the reverse proxy.
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
