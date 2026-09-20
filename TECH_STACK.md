# Tech stack

Every version the AMS build and runtime depend on, and where each one is declared.

Captured **19 September 2026** from `ams-parent-bom/pom.xml`, the Dockerfiles, and the running
containers. Where a version differs between what the build compiles against and what actually runs,
both are listed — those gaps are deliberate and the reason is given.

**Single source of truth:** every dependency version is a property in
[`ams-parent-bom/pom.xml`](ams-parent-bom/pom.xml). No child POM declares a version of its own, so
that file is the only place to change one.

---

## Language and build

| Component | Version | Declared in |
|---|---|---|
| Source / target level | **Java 8** (`1.8`) | `maven.compiler.source` / `.target` |
| Build JDK | Zulu **17.0.18** LTS | `build.sh` (`JAVA_HOME`) |
| Runtime JVM (native) | **Zulu 17.0.18** | `./build.sh run` |
| Runtime JVM (container) | IBM J9 1.8.0_503 | base image |
| Maven | **3.9.16** | developer machine |
| Artifact version | `1.0.0-SNAPSHOT` | `ams.version` |

The build compiles *down* to Java 8 on a JDK 17 toolchain. Compiled classes carry major version
**52**, confirmed against a class in the deployed WAR — so the bytecode level is genuinely 8, not
17 output that happens to run.

## Frameworks

| Component | Version |
|---|---|
| **Struts** | **6.8.0** |
| OGNL | 3.3.5 |
| FreeMarker | 2.3.33 |
| **Spring Framework** | **5.3.39** |
| **Spring Security** | **5.3.13.RELEASE** |
| Spring Security OAuth2 | 2.5.2.RELEASE |
| Jackson | 2.20.2 |
| Log4j2 | 2.26.0 |

## Web tier

| Component | Version | Notes |
|---|---|---|
| Servlet API | **3.1.0** | `javax.*`, `provided` scope, `web-app_3_1` |
| JSTL | 1.2 | |
| javax.annotation | 1.3.2 | |
| Liberty (native) | Open Liberty **24.0.0.12** via `liberty-maven-plugin` 3.11.4 |
| Liberty (container) | WebSphere **26.0.0.8** | `websphere-liberty:26.0.0.8-full-java8-ibmjava` |
| Liberty features | `servlet-3.1`, `jsp-2.3`, `jdbc-4.1`, `jndi-1.0`, `transportSecurity-1.0`, `ssl-1.0` | `server.xml` |
| Dojo Toolkit | **1.17.3** | vendored, ~11,000 files, restored per environment |

## Data

| Component | Version | Notes |
|---|---|---|
| **H2 (runtime + tests)** | **1.3.176** | embedded, file mode; no server, no install |
| Oracle server *(unwired)* | 26ai Free, 23.26.2.0.0 | `docker compose up` only |
| JDBC driver — runtime | **ojdbc8 23.8.0.25.04** | copied into the image; supplied per environment |
| JDBC driver — build/test | ojdbc8 **12.2.0.1** | `ojdbc8.version`, test scope only |
| H2 — tests | 1.3.176 | in-memory, recreated per test JVM, same DDL as runtime |

The two driver versions are intentional and documented in the Dockerfile: the runtime driver matches
the 23ai server it talks to, while the build compiles and tests against the older one. They never
have to agree. `ojdbc8` rather than `ojdbc11` because Liberty's `jdbc-4.1` feature and the Java 8
base image both require it.

## Libraries

| Component | Version |
|---|---|
| commons-lang | **2.5** (`org.apache.commons.lang`) |
| commons-lang3 | **3.17.0** (`org.apache.commons.lang3`) |
| commons-io | 2.17.0 |
| commons-collections4 | 4.5.0 |
| commons-validator | 1.4.0 |
| commons-text | 1.12.0 |
| json-lib / ezmorph | 2.4 / 1.0.6 |
| AspectJ | 1.7.4 |

Both commons-lang generations are on the classpath at once and both are used.

## Test

| Component | Version |
|---|---|
| JUnit | **4.12** |
| Mockito | **1.9.5** (`org.mockito.Matchers`, `MockitoJUnitRunner`) |
| JaCoCo | 0.8.5 |

## Maven plugins

| Plugin | Version | Notes |
|---|---|---|
| maven-war-plugin | **3.4.0** | original was 2.6 |
| maven-ear-plugin | **3.3.0** | original was 2.8 |
| wildfly-maven-plugin | 2.0.2.Final | |
| buildnumber-maven-plugin | 1.3 | |
| maven-checkstyle-plugin | 2.15 (Checkstyle 5.6) | |

The war and ear plugins are the one place the original pinned versions could not be kept: 2.6 and
2.8 date from 2015 and fail to load under Maven 3.9 with a Plexus API incompatibility, so the build
would not run at all. The packaging they produce is the same. The original values are recorded in
the BOM so the change stays visible.

## Containers

| Service | Image | Platform |
|---|---|---|
| `oracle` | `gvenzl/oracle-free:23-slim` | native arm64 |
| `app` | built from `ams-internal/AssetManagementInternalWeb/Dockerfile` | pinned `linux/amd64` |

Both containers pin `TZ=America/New_York`, which has to match `-Duser.timezone` or `TRUNC(x) =
TRUNC(SYSDATE)` day comparisons shift near midnight.

The app image is pinned to `linux/amd64` because no Java 8 Liberty image has an arm64 build — on
Apple Silicon it runs under emulation. That pin disappears the moment the runtime moves off Java 8.

## Modules

Three reactors, built in order by `build.sh`:

```
ams-parent-bom          the BOM; every version property lives here
ams-common              AssetManagementSharedCommon
                        AssetManagementNetworkValidation
                        AssetManagementSharedServices
ams-internal            AssetManagementInternalCommon
                        AssetManagementInternalServices
                        AssetManagementInternalWeb
                        AssetManagementInternalEar
```

---

## Upgrade targets and what blocks them

This section exists because the project is used as input to an automated upgrade. It records the
intended destination and, more usefully, the order the work has to happen in.

| From | To | Blocked by |
|---|---|---|
| Java 8 | Java 21 | base image; `maven.compiler.target`; Liberty feature set |
| Servlet 3.1 `javax.*` | Jakarta EE 9+ `jakarta.*` | nothing — but everything else waits on it |
| Spring 5.3.39 | Spring 6.x | Jakarta namespace |
| Spring Security 5.3.13 | Spring Security 6.x | Spring 6 |
| Struts 6.8.0 | Struts 7.x | Jakarta namespace |
| JUnit 4.12 | JUnit 5 | independent |
| Mockito 1.9.5 | Mockito 5.x | `org.mockito.Matchers` removed; independent |

**The servlet API is the hard edge.** Spring 6 and Struts 7 both require Jakarta EE 9+. Moving
`javax.servlet` → `jakarta.servlet` touches every JSP, filter, listener and `web.xml`, and the
Liberty features have to move with it (`servlet-3.1` → `servlet-6.0`, `jsp-2.3` → `pages-3.1`).
Neither framework upgrade can start before it, and the two cannot be done independently afterwards
either — Struts 7 needs Spring 6 to be on the same namespace.

**The runtime JVM is Java 8, not 17.** Changing `maven.compiler.target` alone produces classes the
container cannot load. The base image has to change in the same commit, which also removes the
`linux/amd64` pin.

**Dojo 1.17.3 pins the content security policy.** `WebSecurityConfig` allows `'unsafe-inline'` and
`'unsafe-eval'` specifically because that build needs both. The policy cannot be tightened until the
toolkit is replaced — though nothing currently loads Dojo, so that replacement is smaller than the
file count suggests.

### Patterns present for the migration to find

Counted across `ams-common` and `ams-internal`, excluding `target/`:

| Pattern | Files |
|---|---|
| `com.opensymphony.*` imports | 53 |
| Field `@Autowired` | 91 |
| `@StrutsParameter` annotations | **0** — every bound setter is unannotated |
| `ModelDriven` actions | 3 |
| `Calendar` / `new Date()` | 17 |
| `SimpleDateFormat` | 4 |

Also throughout: hand-written SQL as concatenated `String` constants, XML Struts configuration,
anonymous `RowMapper` implementations, and `javax.annotation` rather than `jakarta.annotation`.

`@StrutsParameter` being absent everywhere is the one that fails silently. Struts 7 stops injecting
request parameters into unannotated setters — an action still compiles, still runs, and simply
receives nulls for every field the form submitted.
