#!/usr/bin/env bash
# Runs the AMS WAR on Apache Tomcat 9 on macOS: one instance per JDK, all sharing one Tomcat install
# under ~/tools, so more than one can be up at once.
#
#   ./tomcat-mac.sh jdk8  deploy      stop, copy in the freshly built WAR, start        (port 8080)
#   ./tomcat-mac.sh jdk21 deploy                                                         (port 8081)
#   ./tomcat-mac.sh <jdk> start | stop | restart | status
#   ./tomcat-mac.sh <jdk> reset       stop, delete the instance's H2 database, start: the schema and
#                                     seed are rebuilt, which is how a changed seed is picked up
#   ./tomcat-mac.sh <jdk> logs        follow the console log (application + Tomcat)
#
# Typical cycle:   ./build.sh jdk8 && ./tomcat-mac.sh jdk8 deploy        (or just ./build.sh jdk8 run)
#
# Instances (the same ports as the Windows tomcat.sh, so the README's table holds on both):
#   jdk8    http://localhost:8080   ~/tools/tomcat-jdk8     the deployment JDK
#   jdk21   http://localhost:8081   ~/tools/tomcat-jdk21    the same WAR on a current JDK
#   jdk17   http://localhost:8082   ~/tools/tomcat-jdk17    optional third instance
#
# Tomcat 9 rather than 10 or later: the application is Servlet 3.1 / JSP 2.3 on the javax.*
# namespace, and Tomcat 10 moved to jakarta.*. Tomcat 9 runs on Java 8 and later, and the WAR is
# Java 8 bytecode, so every instance above runs the one WAR unchanged.
#
# The server supplies three things the WAR relies on, and each instance recreates them on every run:
#   - the jdbc/amsInternalDS pool behind web.xml's resource-ref. Declared in the WAR's own
#     META-INF/context.xml; this script feeds it an H2 file under the instance's data/ through the
#     AMS_DATASOURCE_URL variable exported from bin/setenv.sh.
#   - the H2 driver that pool loads                      -> instance lib/, copied from ~/.m2
#   - the JVM options (Spring profile, timezone, log dir) -> bin/setenv.sh
#
# HTTP only. TLS is the reverse proxy's job in every real environment, and web.xml no longer forces
# the session cookie Secure, so the application works over plain HTTP here.
#
# Tomcat is downloaded from dlcdn.apache.org into ~/tools/dl and its SHA-512 checked. Everything
# under ~/tools is rebuilt from that archive and the Maven repository; hand edits to server.xml,
# setenv.sh or the stock conf files are overwritten on the next run - change this script instead.
# data/ (the H2 file) and logs/ are kept.
set -euo pipefail

usage() { sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

TOMCAT_VERSION="${TOMCAT_VERSION:-9.0.122}"
TOOLS="${AMS_TOOLS:-$HOME/tools}"
HERE="$(cd "$(dirname "$0")" && pwd)"

JDK_NAME="${1:-}"
CMD="${2:-deploy}"
case "$JDK_NAME" in
  jdk8)  JAVA_VER=1.8; HTTP_PORT=8080; SHUTDOWN_PORT=8005 ;;
  jdk17) JAVA_VER=17;  HTTP_PORT=8082; SHUTDOWN_PORT=8007 ;;
  jdk21) JAVA_VER=21;  HTTP_PORT=8081; SHUTDOWN_PORT=8006 ;;
  *)     usage ;;
esac

APP='AssetManagementInternalWeb'
WAR="$HERE/ams-internal/AssetManagementInternalWeb/target/$APP.war"
CATALINA_HOME="$TOOLS/apache-tomcat-$TOMCAT_VERSION"
CATALINA_BASE="$TOOLS/tomcat-$JDK_NAME"
URL="http://localhost:$HTTP_PORT/$APP"

# ---------------------------------------------------------------- JDK
JAVA_HOME="$(/usr/libexec/java_home -v "$JAVA_VER" 2>/dev/null)" \
  || { echo "!! no JDK $JAVA_VER installed - /usr/libexec/java_home -V lists what is"; exit 1; }
export JAVA_HOME CATALINA_HOME CATALINA_BASE
unset JRE_HOME

# The WAR is built for the Java level in the parent POM (maven.compiler.target, "1.8" -> 8); a JDK
# older than that cannot load a single class of it, so say so here instead of filling the log with
# UnsupportedClassVersionError. (stop/status/logs do not need the JDK and skip the check.)
JAVA_MAJOR="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)(\.([0-9]+))?.*/\1 \3/' \
             | awk '{ print ($1 == 1) ? $2 : $1 }')"
POM="$HERE/ams-parent-bom/pom.xml"
TARGET="$(sed -n 's:.*<maven.compiler.target>\([0-9.]*\)</maven.compiler.target>.*:\1:p' "$POM" | head -1)"
[ -n "$TARGET" ] || TARGET="$(sed -n 's:.*<maven.compiler.release>\([0-9]*\)</maven.compiler.release>.*:\1:p' "$POM" | head -1)"
TARGET="${TARGET#1.}"
check_jdk() {
  if [ -n "$TARGET" ] && [ "$JAVA_MAJOR" -lt "$TARGET" ]; then
    cat <<MSG
!! $JDK_NAME ($("$JAVA_HOME/bin/java" -version 2>&1 | head -1)) is older than the Java $TARGET this
   application is built for (ams-parent-bom/pom.xml), so it cannot run it. Use jdk$TARGET or newer.
MSG
    exit 1
  fi
}

# ---------------------------------------------------------------- install
# Shared binaries: downloaded once, checksum verified, extracted to a versioned directory.
install_home() {
  [ -f "$CATALINA_HOME/bin/catalina.sh" ] && return
  local tgz="$TOOLS/dl/apache-tomcat-$TOMCAT_VERSION.tar.gz"
  local major="${TOMCAT_VERSION%%.*}"
  local path="tomcat/tomcat-$major/v$TOMCAT_VERSION/bin/apache-tomcat-$TOMCAT_VERSION.tar.gz"
  mkdir -p "$TOOLS/dl"
  if [ ! -f "$tgz" ]; then
    echo "==> downloading Tomcat $TOMCAT_VERSION"
    # dlcdn carries only the current release of each branch; the archive carries everything.
    curl -fsSL --retry 3 -o "$tgz" "https://dlcdn.apache.org/$path" \
      || curl -fsSL --retry 3 -o "$tgz" "https://archive.apache.org/dist/$path"
  fi
  curl -fsSL --retry 3 -o "$tgz.sha512" "https://dlcdn.apache.org/$path.sha512" \
    || curl -fsSL --retry 3 -o "$tgz.sha512" "https://archive.apache.org/dist/$path.sha512"
  local want have
  want="$(cut -d' ' -f1 "$tgz.sha512")"
  have="$(shasum -a 512 "$tgz" | cut -d' ' -f1)"
  [ "$want" = "$have" ] || { echo "!! SHA-512 mismatch on $tgz - delete it and retry"; exit 1; }
  echo "==> extracting Tomcat to $CATALINA_HOME"
  tar -xzf "$tgz" -C "$TOOLS"
  [ -f "$CATALINA_HOME/bin/catalina.sh" ] || { echo "!! extraction did not produce $CATALINA_HOME"; exit 1; }
}

# The instance directory. Configuration is rewritten on every run so it always matches this script
# and the Tomcat it runs on - the stock conf files are re-copied from CATALINA_HOME every time, so a
# base that was last used with a different Tomcat version is brought up to date. data/, logs/,
# webapps/ and the certificate are left alone.
install_base() {
  mkdir -p "$CATALINA_BASE"/{bin,conf,lib,logs,temp,webapps,work,data}
  for f in catalina.properties logging.properties web.xml context.xml; do
    cp -f "$CATALINA_HOME/conf/$f" "$CATALINA_BASE/conf/$f"
  done

  # Lets ${AMS_DATASOURCE_*:-default} in the WAR's META-INF/context.xml read the environment that
  # setenv.sh exports. Without this Tomcat consults system properties only.
  local src='org.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource'
  grep -q '^org.apache.tomcat.util.digester.PROPERTY_SOURCE=' "$CATALINA_BASE/conf/catalina.properties" \
    || printf '\n# Added by ams/tomcat-mac.sh: context.xml reads its connection values from the environment.\n%s\n' \
         "$src" >> "$CATALINA_BASE/conf/catalina.properties"

  # The JDBC driver belongs to the server, not the WAR: the pool is in Tomcat's class loader.
  # Same version the build tests against, straight from the Maven repository the build filled.
  local h2v h2jar
  h2v="$(sed -n 's:.*<h2.version>\(.*\)</h2.version>.*:\1:p' "$POM")"
  h2jar="$HOME/.m2/repository/com/h2database/h2/$h2v/h2-$h2v.jar"
  [ -f "$h2jar" ] || { echo "!! no H2 driver at $h2jar - run ./build.sh first"; exit 1; }
  rm -f "$CATALINA_BASE"/lib/h2-*.jar
  cp "$h2jar" "$CATALINA_BASE/lib/"

  cat > "$CATALINA_BASE/conf/server.xml" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<!-- Generated by ams/tomcat-mac.sh - edits are overwritten on the next run. -->
<Server port="$SHUTDOWN_PORT" shutdown="SHUTDOWN">
  <Listener className="org.apache.catalina.startup.VersionLoggerListener"/>
  <Listener className="org.apache.catalina.core.JreMemoryLeakPreventionListener"/>
  <Listener className="org.apache.catalina.mbeans.GlobalResourcesLifecycleListener"/>
  <Listener className="org.apache.catalina.core.ThreadLocalLeakPreventionListener"/>
  <Service name="Catalina">
    <!-- HTTP only: TLS is the reverse proxy's job. -->
    <Connector port="$HTTP_PORT" protocol="HTTP/1.1" connectionTimeout="20000" server="AMS"/>
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="false">
        <Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
               prefix="localhost_access_log" suffix=".txt" pattern="%h %l %u %t &quot;%r&quot; %s %b"/>
      </Host>
    </Engine>
  </Service>
</Server>
XML

  # catalina.sh sources this before it does anything else, so it is also what pins the instance to
  # its JDK regardless of the JAVA_HOME it was started with.
  #   AMS_DATASOURCE_URL   one H2 file per instance: embedded H2 admits a single JVM, so two
  #                        instances cannot share one. Same URL options the tests use.
  #   CATALINA_OUT         everything the JVM prints, Tomcat's and the application's, in one file.
  #   CATALINA_PID         lets "catalina.sh stop -force" finish the job if the shutdown port is deaf.
  cat > "$CATALINA_BASE/bin/setenv.sh" <<SH
#!/usr/bin/env bash
# Generated by ams/tomcat-mac.sh - edits are overwritten on the next run.
export JAVA_HOME="$JAVA_HOME"
unset JRE_HOME
export CATALINA_OUT="$CATALINA_BASE/logs/console.log"
export CATALINA_PID="$CATALINA_BASE/temp/catalina.pid"
export AMS_DATASOURCE_URL="jdbc:h2:$CATALINA_BASE/data/ams;MVCC=TRUE;LOCK_TIMEOUT=5000;DB_CLOSE_ON_EXIT=FALSE"
export CATALINA_OPTS="-Xmx1g -Dspring.profiles.active=local -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Duser.timezone=America/New_York -XX:+ExitOnOutOfMemoryError -DAppLogDir=$CATALINA_BASE/logs"
SH
  chmod +x "$CATALINA_BASE/bin/setenv.sh"
}

# ---------------------------------------------------------------- lifecycle
running() { curl -s -o /dev/null --max-time 3 "http://localhost:$HTTP_PORT/"; }
strays()  { pgrep -f -- "-Dcatalina.base=$CATALINA_BASE( |$)" || true; }

stop_server() {
  if running || [ -s "$CATALINA_BASE/temp/catalina.pid" ]; then
    echo "==> stopping $JDK_NAME instance"
    "$CATALINA_HOME/bin/catalina.sh" stop 30 -force >/dev/null 2>&1 || true
    for _ in $(seq 1 30); do running || break; sleep 1; done
  fi
  # A JVM that never opened its port, or lost its PID file, still holds the H2 lock.
  local pids; pids="$(strays)"
  if [ -n "$pids" ]; then
    echo "    killing stray JVM(s): $pids"
    kill $pids 2>/dev/null || true
    sleep 2
    kill -9 $pids 2>/dev/null || true
  fi
  rm -f "$CATALINA_BASE/temp/catalina.pid"
}

start_server() {
  echo "==> starting $JDK_NAME instance (console output: $CATALINA_BASE/logs/console.log)"
  # catalina.sh start detaches the JVM and sends its output to CATALINA_OUT; the launcher's own
  # few lines go to the same file so the terminal stays quiet.
  "$CATALINA_HOME/bin/catalina.sh" start >>"$CATALINA_BASE/logs/console.log" 2>&1 </dev/null
  # First start unpacks the WAR and builds and seeds the H2 schema.
  printf '    waiting for %s ' "$URL/health"
  for _ in $(seq 1 120); do
    local code; code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$URL/health" || true)"
    if [ "$code" = 200 ]; then
      echo "- up"
      echo "==> $URL"
      return
    fi
    printf '.'; sleep 3
  done
  echo "- not up after 6 minutes; see $CATALINA_BASE/logs"
  exit 1
}

deploy_war() {
  [ -f "$WAR" ] || { echo "!! no WAR at $WAR - build first (./build.sh $JDK_NAME)"; exit 1; }
  echo "==> deploying $WAR"
  rm -rf "$CATALINA_BASE/webapps/$APP" "$CATALINA_BASE/webapps/$APP.war" "$CATALINA_BASE/work/Catalina/localhost/$APP"
  cp "$WAR" "$CATALINA_BASE/webapps/$APP.war"
}

# stop_server runs before install_base rewrites the configuration, so a JVM started from the
# previous configuration (possibly a different Tomcat version) is shut down with the files it knows.
case "$CMD" in
  deploy)  check_jdk; install_home; stop_server; install_base; deploy_war; start_server ;;
  start)   check_jdk; install_home; running && { echo "already running: $URL"; exit 0; }; install_base; start_server ;;
  stop)    stop_server; echo "==> $JDK_NAME instance stopped" ;;
  restart) check_jdk; install_home; stop_server; install_base; start_server ;;
  reset)   check_jdk; install_home; stop_server; install_base
           echo "==> deleting the $JDK_NAME instance's H2 database (rebuilt and reseeded on start)"
           rm -f "$CATALINA_BASE"/data/ams*.db
           start_server ;;
  status)  if running; then echo "$JDK_NAME: running on $URL"; else echo "$JDK_NAME: stopped"; fi ;;
  logs)    tail -F "$CATALINA_BASE/logs/console.log" ;;
  *)       usage ;;
esac
