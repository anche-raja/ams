# AMS Internal Asset Management

A legacy-style Java EE / Spring / Struts 2 hybrid web application, packaged as a WAR (and an EAR)
for WebSphere Liberty. Not a Spring Boot application: there is no embedded server and no fat jar,
and the entire bootstrap is driven by `web.xml`.

Every dependency version, where it is declared, and what blocks each upgrade: [TECH_STACK.md](TECH_STACK.md).

**One flow, the whole stack.** The application does one thing: an operator places a *new install
order* for a customer - site, device, installation appointment - and gets a receipt. That single
flow is kept deliberately small while still passing through every layer of the stack: Struts 2
actions, interceptors, `ModelDriven` session state and a JSON endpoint; Spring Security
pre-authentication and CSRF; the one Spring MVC controller; `@Transactional` services over
hand-written Spring JDBC; the Java port of the scheduling stored procedure; the network
validators; the address validation REST client (Jackson); and the embedded H2 schema. See
[Placing an install order](#placing-an-install-order).

The earlier screens - dashboard, asset search and detail, customer admin, the six-step order,
cancellation, decommission, network change requests, provisioning, calendars, admin utilities and
user impersonation - have been removed, together with the services, DAOs and stored-procedure
ports only they used. The schema is unchanged.

## Layout

Three Maven reactors, built in this order:

| Reactor | Modules |
|---|---|
| `ams-parent-bom` | Parent POM and dependency BOM (`pom` packaging) |
| `ams-common` | `AssetManagementSharedCommon`, `AssetManagementNetworkValidation`, `AssetManagementSharedServices` |
| `ams-internal` | `AssetManagementInternalCommon`, `AssetManagementInternalServices`, `AssetManagementInternalWeb` (war), `AssetManagementInternalEar` (ear) |

## Building

```bash
./build.sh
```

Builds and installs all three reactors in dependency order. `./build.sh package` or
`./build.sh test` work too.

Requires JDK 17 with `JAVA_HOME` pointed at it (the script defaults to a Zulu 17 install) and
Maven 3.9. The compiler targets Java 8 bytecode throughout.

### Windows

`build.sh` hardcodes a macOS layout. On Windows the toolchain lives in `C:\tools`, which an AWS
WorkSpaces restart wipes, so restore it first in every fresh session - it extracts JDK 8, JDK 21,
Maven and a Maven repository snapshot from the zips beside it, sets `JAVA_HOME`, and maps `X:` to
this directory:

```powershell
. 'D:\r.anche\My Files\Home Folder\mysoftware\tools\setup-env.ps1'
```

Then build from Git Bash with the script for the JDK you want:

```bash
./build-jdk8.sh             # compile and package on JDK 8, the deployment JDK
./build-jdk21.sh            # compile and package on JDK 21
./build-jdk8.sh test        # any Maven goal; extra arguments go to Maven
./build-jdk8.sh install -DskipTests
```

Both pin their JDK regardless of the inherited `JAVA_HOME` - JDK 26 is on the system PATH and the
pinned JaCoCo and Mockito fail on it - and both build through `X:`, because the repository's own
path already takes its longest file past `MAX_PATH`. `build.ps1` is the PowerShell equivalent of
`build-jdk21.sh`.

`build-jdk8.sh` runs the tests on JDK 21 (Surefire's `-Djvm`). The parent POM hardcodes
`--add-opens` into the Surefire `argLine` for the pinned Mockito, and Java 8 refuses to start with
that flag; the code under test is still the Java 8 bytecode compiled by JDK 8.

The two scripts share `target\`, so the WAR there is from whichever ran last. Both emit Java 8
bytecode, so either runs on either JDK.

## Architecture

**Two web frameworks side by side.** Struts 2 handles every functional endpoint through
`StrutsPrepareAndExecuteFilter`, mapped to `/*`. Spring MVC's `DispatcherServlet` is mounted only
at `/ams/*` and hosts exactly one `@Controller`, for the two global error pages. There are no
`@RestController` classes. `struts.action.excludePattern` keeps the Struts filter off the Spring
MVC path.

**No ORM.** Persistence is Spring JDBC throughout: `NamedParameterJdbcTemplate`, hand-written
Oracle SQL held as string constants, and manual `RowMapper` implementations. Transactions are
managed by `DataSourceTransactionManager`. There is no JPA, no Hibernate, no `.hbm.xml` and no
`@Entity` anywhere, and the build asserts none of them are on the classpath.

**No Lombok.** Every domain object has hand-written accessors, `implements Serializable` and an
explicit `serialVersionUID`.

**Pre-authenticated SSO.** There is no login form. A reverse proxy authenticates the caller and
forwards `iv-user` / `iv-groups` headers; `WebSealRequestHeaderAuthenticationFilter` reads them and
`AmsUserDetailsService` resolves the directory groups into roles through a database mapping table.
Authorisation is one role per action — about fifty `SecurityRoleType` constants — checked per
action method rather than by URL pattern.

**Legacy typesafe enums.** `LoadableType` is an abstract base carrying a code, a description and a
database surrogate key; subclasses expose `public static final` singletons registered in a
`LinkedHashMap` so `values()` keeps declaration order for drop-downs.

## The database

Embedded **H2**, in file mode, built at startup by
`ams-common/.../shared/schema/SchemaInstaller` from the SQL in that module's main resources. There
is no Oracle, no container, and no migration tool.

| Directory | Contents |
|---|---|
| `db/schema/01_tables/` | 35 tables |
| `db/schema/02_constraints/` | 21 check constraints, 32 foreign keys |
| `db/schema/03_indexes/` | 41 indexes |
| `db/schema/04_sequences/` | 19 sequences |
| `db/schema/05_views/` | `AMS_NCR_SCHEDULES_EXT_V` |
| `db/seed/{core,demo,rolling}/` | see *One schema, two consumers* below |

The DDL is the Oracle DDL, unchanged. H2 1.3.176 accepts `VARCHAR2(n CHAR)`, `NUMBER(19)`,
`CREATE TABLE IF NOT EXISTS`, `SYSTIMESTAMP`, `DUAL`, `NVL`, `TRUNC`, `GREATEST` and `ROWNUM`
natively, with no `MODE=Oracle` - which is why the same statements the DAOs issue have always run
against both. The only casualty is six function-based indexes, which H2 has no equivalent for; they
are commented rather than deleted, and `LDAPROLES_GROUP_IX` is the one that would matter at scale.

**The stored procedures are now Java.** The ones the install order uses live in
`ams-common/.../shared/dao/scheduling`: `TimeslotSchedulingDAO` (reserve and cancel a calendar
place) and `EntityEmailDAO` (queue a notification). The shared `StoredProcedureDAO` interface and
its bean name are unchanged. The network change and decommission procedures went with the screens
that called them; the original PL/SQL for all nine is kept under `db/oracle/06_packages` as the
specification the port was written from.

Four properties carried over and are the ones to protect:

- **The ledger is the source of truth, not the counter.** `AMS_TIMESLOT_RESERVATIONS` records who
  holds each place, which is what makes a double reserve take no second place and a double cancel
  harmless. Without it a cancel would decrement blind and eventually hand a place out twice.
- **`SELECT ... FOR UPDATE` around the capacity test and the increment**, with the five-second bound
  that used to be `WAIT 5` now on the connection URL as `LOCK_TIMEOUT=5000`. It exists because
  Liberty's `connectionTimeout` bounds pool waits, not query waits.
- **`TIMESLOTS_RESERVED_CK` is the backstop.** A counter bug fails loudly instead of double-booking
  an engineer.
- **`AVAILABLE_FL` is never written.** It means "ops opened this slot", not "this slot has room".

Nothing commits. Each operation takes a savepoint on the caller's connection and rolls back to it on
any outcome other than `OK`, so a non-`OK` status reliably means nothing changed - placing an
install order writes the order, its installation and the reservation as one abandonable unit.

## Running

```bash
./build.sh run     # builds, then starts the application natively - no Docker, no Oracle
./build.sh stop    # stops it
```

That is the whole story: a JDK and Maven, nothing else. Liberty is fetched by
`liberty-maven-plugin` and the database is an embedded H2 file under the server directory, built and
seeded on first start by `SchemaInstaller`.

`liberty:dev` is what `run` uses, deliberately rather than `liberty:run`: it deploys the application
loose, from `target/classes` and `src/main/webapp`, instead of copying the 37 MB WAR on every cycle -
almost all of which is the vendored Dojo tree.

Embedded H2 is a file, and exactly one JVM may hold it. A server left behind by an earlier run keeps
the lock and the next start fails with "Database may be already in use", which names the symptom and
not the cause - so `run` and `stop` both clear strays first.

`build-jdk8.sh run` / `build-jdk21.sh run` (and `stop`) do the same on Windows.

### On Tomcat 9 (Windows)

The WAR also runs on Apache Tomcat 9, on either JDK, with no change to the application. Two
instances share one Tomcat install, so both can be up at once:

| Instance | JDK | URL | Directory |
|---|---|---|---|
| `jdk8` | 8 | http://localhost:8080/AssetManagementInternalWeb | `C:\tools\tomcat-jdk8` |
| `jdk21` | 21 | http://localhost:8081/AssetManagementInternalWeb | `C:\tools\tomcat-jdk21` |

```bash
./build-jdk8.sh && ./tomcat.sh jdk8 deploy   # build, then deploy and start on JDK 8
./tomcat.sh jdk8 deploy     # stop, copy in target\AssetManagementInternalWeb.war, start
./tomcat.sh jdk8 start      # start without redeploying
./tomcat.sh jdk8 stop
./tomcat.sh jdk8 restart
./tomcat.sh jdk8 status
./tomcat.sh jdk8 logs       # follow logs\console.log
```

Substitute `jdk21` for the other instance. `start` and `deploy` return once `/health` answers.

Tomcat 9 rather than 10 or later: the application is Servlet 3.1 / JSP 2.3 on `javax.*`, and
Tomcat 10 moved to `jakarta.*`. Each instance recreates what Liberty supplied - the
`jdbc/amsInternalDS` pool behind `web.xml`'s `resource-ref` (a context descriptor under
`conf\Catalina\localhost`), the H2 driver (in the instance `lib\`) and `jvm.options` (in
`bin\setenv.bat`, including `-Dspring.profiles.active=local`). Each has its own H2 file under
`data\`, since embedded H2 admits one JVM. Only HTTP is configured.

`tomcat.sh` extracts Tomcat from `tools\dl\tomcat.zip` when `C:\tools\tomcat` is missing and
rewrites each instance's configuration on every run, so it needs nothing after a restart beyond
`setup-env.ps1`. Hand edits to `server.xml`, the context descriptor or `setenv.bat` are overwritten;
change the script instead.

Console output goes to `logs\console.log`, not the terminal. The application's log4j console
appender holds a lock while it writes, so a JVM left writing to a terminal pipe that nobody reads
any more hangs every request thread once the pipe fills.

### The Oracle stack

`docker-compose.yml` and `db/oracle` are kept but unwired. The container path still works -
`server.xml` takes its JDBC driver class from a variable and compose sets it to Oracle - but nothing
maintains it, and the PL/SQL under `db/oracle/06_packages` is now reference material rather than
running code: it is the specification the Java port was written from.

```bash
./build.sh docker
```

## Deploying

The image is built from `AssetManagementInternalWeb/Dockerfile` on
`websphere-liberty:26.0.0.8-full-java8-ibmjava`, listening on 9081 (HTTP) and 9444 (HTTPS,
TLSv1.2). Liberty configuration is in `src/main/liberty/config`.

Two files are **not** in this repository and must be supplied before the image will build — each
directory has a README explaining what belongs there:

- `AssetManagementInternalWeb/lib/ojdbc8-23.8.0.25.04.jar` — the runtime Oracle driver, matching
  the 23ai server. Not redistributable, which is why the build resolves 12.2.0.1 from Maven
  Central for tests only; the two never have to agree.
- `AssetManagementInternalWeb/certs/internal-ca.crt` — the internal CA the proxy and the platform
  REST services are signed by. For local use, any self-signed certificate will do.

Database connection details and the keystore password come from the environment through
`bootstrap.properties`. No credential is committed; `docker-compose.yml` carries development
defaults for a throwaway local database only.

The Spring profile selects the security wiring: `production` and `qa` register the real
header-reading filter, while `local`, `dev` and `fit` register a developer stub that asserts a
fixed identity when no proxy is in front of the container. Set it in `jvm.options`.

## Testing

242 tests across the five code modules.

DAO and service integration tests run against an embedded H2 database created by the DDL scripts
under `src/test/resources/sql` — one file per table, plus sequences, a view and seed data, wired up
by `test-context-h2.xml`. The internal module reuses the shared module's scripts through its
test-jar rather than keeping a second copy that could drift.

Every statement the DAOs issue is written in the subset both Oracle and H2 accept, so the SQL that
runs in the tests is the SQL that runs in production.

The web module's tests stand the real Spring Security filter chain up and drive requests through
it, and walk the Struts configuration asserting that every action class, every action method and
every result JSP actually exists.

## Deviations from the specification

Five, all deliberate:

1. **Packaging plugin versions.** `maven-war-plugin` 2.6 and `maven-ear-plugin` 2.8 cannot load
   under Maven 3.9 — they fail with a Plexus API incompatibility before the build starts. Bumped
   to 3.4.0 and 3.3.0; the original values are recorded in a comment in the parent POM. Every other
   pinned version is exactly as specified.

2. **Base image.** The specified `websphere-liberty:26.0.0.2-full-java8-openj9-ubi-minimal` does
   not exist, and neither does any java8 + openj9 combination — IBM publishes Java 8 Liberty on
   IBM Java only, with OpenJ9 variants starting at Java 11. Using
   `26.0.0.8-full-java8-ibmjava` instead: same Liberty feature set, same Java 8. Java 8 was kept
   rather than moving to a Java 17 image because the whole build targets it
   (`maven.compiler.target`, the `jdbc-4.1` feature, `ojdbc8`). The cost is that no Java 8 Liberty
   image is built for arm64, so on Apple Silicon that one container runs emulated — see the
   `platform` pin in `docker-compose.yml`, which should be removed on an amd64 host.

3. **Runtime JDBC driver.** `ojdbc8-23.8.0.25.04` rather than the specified 21.5.0.0, matching the
   Oracle 23ai server the compose stack runs. The BOM's test-scope pin stays at 12.2.0.1 as
   specified; the two are independent.

4. **Authenticated principal types.** `AmsUser`, `AmsRole` and `WebSealPrincipal` live in
   `AssetManagementInternalCommon` rather than in the web module's `web.security` package. The
   services module resolves roles and has to return an `AmsUser`, and it cannot depend on the WAR.
   The filters, provider and CSRF matcher are in `web.security` as specified.

5. **Dojo Toolkit.** `js/dojo-release-1.17.3/` is present but empty, with a README explaining what
   belongs there. The toolkit is a third-party distribution of several thousand files.
   `js/common.js` is written against the DOM directly and does not depend on it.

## Placing an install order

Start from the home page (`/Home.action`, where the context root redirects): it lists the customers,
and **New install order** against one of them makes it the session's customer and opens the flow.
Each step validates its own input; the partly completed order lives in the session, so nothing is
written until it is placed and an abandoned order leaves no rows behind.

| Step | Screen | What it collects | What it exercises |
|---|---|---|---|
| 1 | Site | Site contact and installation address | address validation interceptor and REST client (Jackson); commons-validator |
| 2 | Device | Nickname, WAN and LAN addressing | the `AssetManagementNetworkValidation` module (commons-lang 2) |
| 3 | Appointment | An installation slot, then **Place order** | a JSON action behind the AJAX-token stack; the scheduling port |
| - | Confirmation | - (the receipt, read back from the database) | the detail read across five tables |

There is **no review step**. Every step validates as it is left and Place order re-validates the
whole model, so a review page would only repeat what the user had just been through.

Placing the order is one transaction in `OrderServiceImpl.placeInstallOrder`: the site address and
contact, the order row, the device configuration, an `AMS_INSTALLATIONS` row, the `INSTALL`
reservation through `TimeslotSchedulingDAO` (which moves both the installation and the order to
`SCHEDULED`), an audit event and a queued confirmation email.

Three things are worth knowing about how this behaves:

- **The appointment is not held while it is being looked at.** The slots are fetched as JSON when
  the page opens and reserved only when the order is placed. If the chosen one fills in between,
  the order is still placed - discarding three screens of keyed data over an engineer's morning
  would be the wrong trade - but the installation stays `NOTSCHED` and the confirmation says so.
- **A ZIP code with no engineer region yields no slots.** That is a gap in `AMS_INSTALL_REGIONS`,
  not an error: the order can still be placed and operations book the visit by hand. The seeded
  regions cover 78701, 62704, 73301 and 60601 (CENTRAL), 10001, 02108 and 19103 (NORTHEAST), and
  97201, 98101 and 94105 (WEST).
- **Address validation never blocks an order.** A correction is offered on the site page for the
  user to accept or refuse, and an outage lets the address through marked unverified. Locally the
  stub service offers a correction for most addresses, so expect to be asked.

## One schema, two consumers

`ams-common/AssetManagementSharedServices/src/main/resources/db` is the schema, and both the tests
and the running server load the identical files from it over the ordinary compile dependency. There
used to be a second copy under `src/test/resources/sql` describing the same tables; it was the
subset, missing both ledger tables and all 53 constraints, and it is gone.

The seed is tiered, because the three tiers have genuinely different lifetimes:

| Tier | When it runs | Who gets it |
|---|---|---|
| `db/seed/core` | once, on an empty database | tests and the running app |
| `db/seed/demo` | once, on an empty database | non-production profiles only |
| `db/seed/rolling` | **every start** | non-production profiles only |

The rolling tier is calendar capacity, generated relative to today. The Oracle original made it once
at container first boot, which meant that on a database more than three weeks old the despatch and
installation screens were silently empty. A persistent embedded file makes that more likely, not
less. The scripts clear only future slots nobody holds, so re-applying never strands a booking.

## Demonstration data

The original seed (`07_seed/17` to `32`) is deliberately sparse: every row in it is load-bearing
for some DAO test, so it is not safe to add to and not much to look at. Two later files exist for
driving the portal instead.

`07_seed/38_AMS_DEMO_LIFECYCLE.sql` adds four customers, each parked at a different point in the
lifecycle:

| Customer | Region | State |
|---|---|---|
| Northwind Coffee Roasters | WEST | live and healthy — order completed, asset active, install done |
| Beacon Hill Physio | NORTHEAST | order in flight — despatch window held, installation not yet booked |
| Lakeshore Legal Partners | CENTRAL | steady state — two assets, config revisions, open and closed change requests |
| Sundial Grocery Co-op | WEST | end of life — decommission scheduled, RMA issued, replacement ordered |

Nothing there is referenced by a test, so it can be changed freely. Ids sit in the range 1010–9199,
above the original seed and below 100000 where the sequences start, so nothing the running
application creates can collide with it.

Two details are worth knowing:

- **It is re-runnable.** Everything in those id ranges is deleted first, in reverse foreign-key
  order. Calendar places are given back through `AMS_SCHEDULING_PG.cancel_timeslot` rather than by
  deleting the ledger rows, because deleting them directly would leave `RESERVED_COUNT` overstated
  and slowly close the calendar for everyone. Re-running leaves the total reserved count unchanged.
- **The despatch windows are booked through the real procedure**, not by writing the ledger row and
  bumping the counter by hand. The counter and the ledger therefore agree by construction, and the
  seed exercises the same code path the ordering flow uses — so a signature change breaks the
  container build rather than surfacing on someone's screen.

`07_seed/37_AMS_FACILITATION_WINDOWS.sql` generates rolling installation and tech-line capacity.
This one fixes a dead end rather than adding decoration: `07_seed/14` seeds four slots on fixed
September 2025 dates, which is right for the DAO tests but means that from the day after seeding
both calendars are empty and an order can be placed but never scheduled. The gate now asserts that
future bookable installation and tech-line windows exist, for the same reason it already asserted
it for despatch windows.

To load both against a database that is already running:

```bash
docker compose exec -T oracle sqlplus -S ams/ams_app_pw@//localhost:1521/FREEPDB1 \
  @/opt/ams/sql/07_seed/37_AMS_FACILITATION_WINDOWS.sql
```

On a fresh volume `00_init.sh` runs them in filename order along with everything else, but only
when `AMS_CUSTOMERS` is empty.

### Refreshing the calendars

The despatch, installation and tech-line windows are generated relative to `SYSDATE`, and the seed
only runs on an empty database — so on a volume that has been up for a few weeks they age into the
past and the calendars quietly empty out. Roll them forward with:

```bash
for f in 36_AMS_SHIPPING_WINDOWS 37_AMS_FACILITATION_WINDOWS; do
  printf 'SET DEFINE OFF\n@/opt/ams/sql/07_seed/%s.sql\nCOMMIT;\nEXIT\n' "$f" \
    | docker compose exec -T oracle sqlplus -S ams/ams_app_pw@//localhost:1521/FREEPDB1
done
```

Both files clear only future slots that nobody holds, so a window an order is relying on is never
taken away. Aged-out windows in the past are normal and are not an error — `validate.sql` reports
how many have aged out but only fails when there are no bookable future ones left.

## Verification status

After the reduction to the install order flow, on the native stack (`./build.sh run`, embedded H2,
fresh database), 25 September 2026:

| Check | Result |
|---|---|
| `./build.sh` - all three reactors | 242 tests, 0 failures |
| Install order end to end over HTTPS | order placed; receipt shows site, contact, WAN/LAN, notes and the booked slot |
| Appointment reservation | order and installation both `SCHEDULED` through the scheduling port |
| Field validation | contact, address, WAN (RFC 1918) and LAN type A errors shown on the right step |
| Skipping ahead by URL | redirected to the first incomplete step; no order in progress goes home |
| Address correction offered | shown on the site page; accepting it continues to the device step |
| `AppointmentSlots` without the AJAX token | JSON `invalidSession` body, which the page answers by reloading |
| POST without a CSRF token | `403` |
| Replaying Place order | no second order - the model is gone and the flow restarts |
| Another customer's order id in the URL | "That order could not be found." |
| `/health`, Spring MVC `/ams/error` | `200` |

The table below records the verification of the full application, before the reduction, against the
Oracle container. Rows about screens that no longer exist are historical.

Confirmed against a running stack, not just by test:

| Check | Result |
|---|---|
| `./build.sh` — all three reactors | 284 tests, 0 failures |
| Oracle schema built by the container | 35 tables, 18 sequences, 1 view |
| Schema built once, as `AMS` in `FREEPDB1` | no second pass as `SYS` in the CDB root |
| Ordering flow, all six steps, end to end | order placed; every association persisted |
| Address validation — correction offered | suggestion page shown, accepted, stored `VALIDATED_FL = 'Y'` |
| Address validation — service unavailable | "not verified" page; order still placeable |
| Despatch window reservation | `AMS_TIMESLOT_RESERVATIONS` row `HELD`, `RESERVED_COUNT` incremented |
| PL/SQL objects | 3 packages + 3 bodies, **0 invalid**, 0 compilation errors |
| All 9 procedures smoke-called | every one returns its expected status |
| Demo data, four customers | every screen renders their rows; both open orders hold a real despatch window |
| Demo seed re-run | no duplicates, total `RESERVED_COUNT` unchanged |
| Installation calendar | 54 bookable slots returned per customer — was 0 before `07_seed/37` |
| Switching customer mid-order | in-progress order discarded; the next order is attributed to the customer on screen |
| `/health` and `/health.action` unauthenticated | `200 OK`, body `OK` |
| Production profile, no proxy headers | `403` on every other path, no login redirect |
| Production profile, `iv-user: Unauthenticated` | `403` — the proxy's "not signed in" literal is not an identity |
| Production profile, real headers | request served |
| Application → Oracle | a live search returns seeded rows; JDBC driver reports 23.8.0.25.04 |

The end-to-end ordering check is a scripted walk through all six screens against the running
container. It places a real order and then reads the row back: the three contacts, the shipping
address, the maintenance window, the configuration, both subscriber PCs, the despatch window and
its ledger entry, and the queued confirmation email are all confirmed in the database rather than
inferred from the confirmation page.

The procedure smoke test covers the cases mocks cannot: reserving a full slot returns
`NO_CAPACITY`, reserving twice does not double-count, cancelling twice returns `NOT_RESERVED` and
leaves the counter intact, a decommission beyond the 42-day window is refused, and a repeated
notification is suppressed rather than queued again.

### Known wart

Liberty writes an FFDC incident for `DSRA9010E: 'setReadOnly' is not supported` on every
`@Transactional(readOnly = true)` entry. Spring catches it and logs at debug — the request
succeeds — but the incident files accumulate. Fixing it properly means switching the datasource
`res-sharing-scope` to `Unshareable`, which changes connection-pool behaviour, so it is left
alone deliberately rather than traded for a worse problem.
