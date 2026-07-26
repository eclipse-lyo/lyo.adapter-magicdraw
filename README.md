# OSLC MagicDraw SysML Adapter

This repository provides an OSLC server for SysML resources. It can run with a
standalone XML repository for development, demonstrations, and tests, or with
the optional MagicDraw Java API repository when the proprietary SDK is
available.

The project currently targets Eclipse Lyo `7.0.0.Beta1` and Jakarta REST.

## Getting started

From the repository root:

```powershell
mvn clean package jetty:run-war -am -pl :oslc4jmagicdraw -DskipTests
```

Useful endpoints include:

- http://localhost:8080/services/catalog/singleton
- http://localhost:8080/services/serviceProviders/SUV_Example
- http://localhost:8080/services/SUV_Example/blocks

The standalone repository supports the active create operations and value
property updates in memory. It also updates the corresponding owning-block
links for properties, ports, and connectors. An explicit repository refresh
reloads the configured fixture and therefore discards those in-memory changes.

To use another port:

```powershell
mvn clean package jetty:run-war -am -pl :oslc4jmagicdraw -DskipTests `
  "-Djetty.http.port=8181" "-Dsysml.adapter.port=8181"
```

## MagicDraw versions targeted

The original adapter targeted MagicDraw 17.0.3, 17.0.4, 17.0.5, and 18.0 and
required the MagicDraw SysML plugin. The build files preserved in this
repository identify these more specific installation variants:

| Maven profile | Historical target | Default installation directory |
| --- | --- | --- |
| `magicdraw-17.0.3-sp1` | MagicDraw 17.0.3 SP1 | `C:/Program Files/MagicDraw UML` |
| `magicdraw-17.0.4-sp2` | MagicDraw 17.0.4 SP2 | `C:/Program Files/MagicDraw UML17.0.4 SP2` |
| `magicdraw-17.0.5-sp1` | MagicDraw 17.0.5 SP1 | `C:/Program Files/MagicDraw` |
| `magicdraw-18.0.1-sp1` | MagicDraw 18.0.1 SP1, named by the historical README | `C:/Program Files/MagicDraw` |
| `magicdraw-18.0-sp6` | MagicDraw 18.0 SP6 LTR, reflected by the last legacy main-POM update | `C:/Program Files/MagicDraw` |

These are historical compatibility targets, not claims of verification against
the current Java 17/Jakarta migration. A build and runtime test with the
corresponding proprietary installation is still required.

## Repository architecture

- `edu.gatech.mbsec.adapter.magicdraw.repo-api` defines `SysmlRepository` and
  the shared configuration contract.
- `edu.gatech.mbsec.adapter.magicdraw` contains the OSLC server and the
  standalone repository.
- `edu.gatech.mbsec.adapter.magicdraw.repo-magicdraw` contains the recovered
  MagicDraw Java API implementation. It is excluded from the default reactor.
- `edu.gatech.mbsec.adapter.magicdraw.distribution` overlays the server WAR and
  packages the MagicDraw implementation. It is enabled with the generic
  `non-standalone` profile or any version-specific MagicDraw profile.

There is one JAX-RS application and one manager path for both repository
implementations.

### Projects and models

A project identifier is repository metadata used to partition resources and to
identify an OSLC Service Provider. It is not an Ecore resource or containment
relationship.

- The standalone repository reads the identifier from each `<project id="...">`
  element in `sysml-workdir.xml`.
- The MagicDraw repository derives it from the discovered `.mdzip` file. In the
  legacy individual-SVN-file mode, the configured directory identity is also
  included.

Models, item flows, and the other published SysML resources are queried within
that project partition.

The default fixture is
`edu.gatech.mbsec.adapter.magicdraw/src/main/resources/sysml-workdir.xml`.
It contains the `SUV_Example` project.


## Implemented HTTP endpoints

The following project-scoped routes are registered below
`/services/{projectId}`. A check means that an active JAX-RS method is present;
the two diagram routes currently serve HTML only.

| SysML resource | Route | GET | POST | PUT | DELETE |
| --- | --- | :---: | :---: | :---: | :---: |
| Model | `/model` | ✓ |  |  |  |
| Package | `/packages` | ✓ | ✓ |  |  |
| Requirement | `/requirements` | ✓ | ✓ |  |  |
| Block | `/blocks` | ✓ | ✓ |  |  |
| Interface block | `/interfaceblocks` | ✓ | ✓ |  |  |
| Association block | `/associationblocks` | ✓ |  |  |  |
| Part property | `/partproperties` | ✓ | ✓ |  |  |
| Reference property | `/referenceproperties` | ✓ | ✓ |  |  |
| Flow property | `/flowproperties` | ✓ | ✓ |  |  |
| Value property | `/valueproperties` | ✓ | ✓ | ✓ |  |
| Port | `/ports` | ✓ | ✓ |  |  |
| Proxy port | `/proxyports` | ✓ |  |  |  |
| Full port | `/fullports` | ✓ |  |  |  |
| Connector | `/connectors` | ✓ | ✓ |  |  |
| Connector end | `/connectorends` | ✓ | ✓ |  |  |
| Item flow | `/itemflows` | ✓ | ✓ |  |  |
| Value type | `/valuetypes` | ✓ |  |  |  |
| Block diagram | `/blockdiagrams` | HTML |  |  |  |
| Internal block diagram | `/internalblockdiagrams` | HTML |  |  |  |

Collection/query and individual-resource GETs share each route; individual
resources append their qualified name. Service-provider discovery starts at
`/services/catalog/singleton`, with service providers under
`/services/serviceProviders/{projectId}`. Resource shapes are exposed under
`/services/resourceShapes`.

The resource model also defines associations, bound references, flow
directions, units, and quantity kinds. They may appear within returned
resources, but there is no registered, dedicated JAX-RS route for them.

## Configuration

Each value is resolved in this order:

1. environment variable;
2. JVM system property;
3. `config.properties`;
4. default.

The properties file can be selected with `SYSML_ADAPTER_CONFIG_FILE` or
`sysml.adapter.configFile`. If neither is set, the legacy
`oslc4jmagicdraw configuration/config.properties` locations are checked.

| Purpose | Environment variable | JVM property | File property |
| --- | --- | --- | --- |
| Repository implementation | `SYSML_REPOSITORY_CLASS` | `sysml.repository.class` | `sysml.repository.class` |
| Standalone XML file | `SYSML_REPOSITORY_MODEL_FILE` | `sysml.repository.modelFile` | `standaloneModelFile` |
| Models directory | `SYSML_MAGICDRAW_MODELS_DIRECTORY` | `sysml.magicdraw.modelsDirectory` | `magicdrawModelsDirectory` |
| Public URI | `SYSML_ADAPTER_PUBLIC_URI` | `sysml.adapter.publicUri` | `publicURI` |
| Adapter port | `SYSML_ADAPTER_PORT` | `sysml.adapter.port` | `portNumber` |
| Refresh interval | `SYSML_REFRESH_INTERVAL_SECONDS` | `sysml.refresh.intervalSeconds` | `delayInSecondsBetweenDataRefresh` |
| Ecore location | `SYSML_ECORE_LOCATION` | `sysml.ecore.location` | `sysmlEcoreLocation` |

The selected repository class is logged during startup. An explicitly
configured class or model file that cannot be loaded fails startup instead of
silently falling back.

## MagicDraw Java API repository

MagicDraw's own POM comments and Java package names call this extension API
"OpenAPI" (for example, `com.nomagic.magicdraw.openapi.uml` and the historical
"MagicDraw OpenAPI UserGuide"). This is unrelated to the OpenAPI
Specification/Swagger. The original README used the less ambiguous term
"MagicDraw API."

The recovered implementation is in the normal Maven source layout at
`edu.gatech.mbsec.adapter.magicdraw.repo-magicdraw/src/main/java`. It contains
the MagicDraw project loading, resource mapping, lookup, and mutation logic
directly; there is no preserved-source directory, duplicate application class,
or legacy-manager delegate.

The distribution packages the JARs under the configured MagicDraw
installation's `lib`, `lib/graphics`, `lib/webservice`, and SysML plugin
directories. These libraries are installation-supplied and cannot be resolved
transitively from the repository implementation module.

Select a historical installation with its Maven profile; no POM copying,
renaming, or deletion is required. Select exactly one version profile (or the
generic `non-standalone` profile), because each selector activates the same two
MagicDraw modules:

```powershell
mvn clean package "-Pmagicdraw-17.0.5-sp1" -DskipTests
```

Override the profile's installation directory when necessary:

```powershell
mvn clean package "-Pmagicdraw-17.0.5-sp1" -DskipTests `
  "-Dmagicdraw.installdir=D:/Applications/MagicDraw"
```

For an installation not represented by a historical profile, use the generic
profile and supply both properties:

```powershell
mvn clean package -Pnon-standalone -DskipTests `
  "-Dmagicdraw.version=custom" `
  "-Dmagicdraw.installdir=D:/Applications/MagicDraw"
```

Run the selected distribution with:

```powershell
mvn clean package jetty:run-war "-Pmagicdraw-17.0.5-sp1" -am `
  -pl :oslc4jmagicdraw-magicdraw -DskipTests `
  "-Dsysml.repository.class=edu.gatech.mbsec.adapter.magicdraw.repository.SysmlRepositoryMagicDrawImpl"
```

### Legacy POM reconciliation

The former version-specific POM files have been folded into the active reactor
and removed. Their contents map to the current build as follows:

| Former POM concern | Active representation |
| --- | --- |
| Version and default installation directory | Root `magicdraw-*` profiles |
| Per-installation JAR lists under `lib`, `lib/graphics`, `lib/webservice`, and the SysML plugin | Directory-based WAR resources, flattened into `WEB-INF/lib` |
| Additional 17.0.3 SP1 dependency-matrix, diagram-table, and relationship-map JARs | Resources activated by `magicdraw-17.0.3-sp1` |
| MagicDraw compile-time types | Minimal system-scoped SDK dependencies in `repo-magicdraw`, versioned by `${magicdraw.version}` |
| Log4j 1.2.15 replacement | `reload4j` 1.2.25 in the distribution; `log4j-1.2.15.jar` excluded |
| Servlet, JAX-RS, Lyo, resource-module, and Jetty dependencies | Their current Jakarta, Jersey 3, Lyo 7 BOM, reactor-module, and Jetty 12 equivalents |

The obsolete Wink/JSR-311 and Mortbay Jetty definitions were not duplicated in
the profiles because they cannot coexist with the current Jakarta application;
their supported behavior is supplied by the active server POM.

## Subversion configuration

The historical application supported synchronizing whole repositories and
individual files through two separate Subversion projects. Those projects are
not present in this migrated reactor. The URL-list page is retained, but
repository refresh reads files already present in the configured models
directory and does not perform an SVN checkout/update.

If either legacy SVN mode is enabled, startup logs a warning describing this
limitation. This avoids silently presenting a local refresh as a successful SVN
synchronization.

## Verification

Run unit and standalone repository contract tests:

```powershell
mvn test -DskipITs
```

Run the HTTP acceptance tests:

```powershell
mvn verify -Pacceptance
```
