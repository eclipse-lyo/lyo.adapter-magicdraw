# oslc-adapter-magicdraw-sysml — Security Audit (Lyo 7.x)

- **Repo:** `oslc-adapter-magicdraw-sysml` (`/workspace/oslc/lyo-misc/oslc-adapter-magicdraw-sysml`)
- **Lyo version:** 7.0.0-Alpha.10
- **Type:** OSLC adapter (MagicDraw/SysML)
- **Audit basis:** OWASP WSTG v4.2
- **Status:** Initial pass (turn 1)

## 1. Scope & Methodology
Static review of `edu.gatech.mbsec.adapter.magicdraw`. WSTG: INPV (XXE, path), CRYP (log4j), ATHN. See `AUDIT_INDEX.md`.

## 2. Authentication & OAuth Model
Lyo OAuth 1.0a server. See cross-cutting C1.

## 3. Findings Summary
| ID | WSTG | Sev | Location | Issue |
|---|---|---|---|---|
| F1 | INPV-07 | **Medium** | `.../SysmlRepositoryStandaloneImpl.java:194` | `DocumentBuilderFactory` (XXE verify) |
| F2 | CRYP / Log4j | **Medium** | MagicDraw distribution | Bundled log4j 1.x (CVE-2019-17571 / CVE-2021-4104) |
| F3 | INPV-10 | **Medium** | `OSLC4MBSESpecificationService.java:89`, `ServiceUtil.java:90`, `SysMLSVNFileURLService.java:192` | Ecore/file paths from URIs |
| F4 | ATHN-01 | **Medium** | OAuth 1.0a | See cross-cutting C1 |

## 4. Detailed Findings

### F1 — XML parser [WSTG-INPV-07] — Medium
`SysmlRepositoryStandaloneImpl.java:194` instantiates `DocumentBuilderFactory` without confirmed secure features while loading models. Verify XXE hardening; the same concern applies wherever Ecore/XML is parsed.
**Recommendation:** Use a hardened factory builder; `disallow-doctype-decl=true`, etc.

### F2 — Bundled log4j 1.2.15 [WSTG-CRYP / Log4j] — Medium — ✅ FIXED (Maven side, this turn)
POMs reference `log4j-1.2.15` from the MagicDraw install dir via `systemPath`. log4j 1.2.x is EOL with CVE-2019-17571 (SocketServer RCE) and CVE-2021-4104 (JMSAppender). The effective version depends on the MagicDraw bundle shipped at runtime.
**✅ Remediated (build side):** the active MagicDraw distribution depends on `ch.qos.reload4j:reload4j:1.2.25` and excludes `log4j-1.2.15.jar` while packaging the installation JARs. The superseded per-version POMs were folded into Maven profiles and removed. No source changes were required. **Caveat:** MagicDraw may still supply its own `log4j 1.2.x` outside the WAR classpath, so the runtime installation itself must also be patched/replaced.
**Recommendation:** Patch or replace the MagicDraw-shipped log4j; avoid `SocketServer`/`JMSAppender`.

### F3 — File/path construction [WSTG-INPV-10] — Medium
`loadEcoreModel(URI.createFileURI(...))`, `new File(OSLC4JMagicDrawApplication.magi...)` etc. resolve model files from configured/supplied URIs. Ensure resolution is confined to allowed roots and rejects traversal.
**Recommendation:** Canonicalize and confine; validate URIs.

## 5. Cross-cutting
See `AUDIT_INDEX.md` C1 (anonymous `requestKey`), C2 (log4j), C3 (XXE).

## 6. Next steps
- Confirm XXE hardening on all `DocumentBuilderFactory`/`TransformerFactory` uses.
- Review `SysMLSVNFileURLService` for SVN URL/credential handling.

## 7. SysML v2 API Surface Review — 2026-09-29

This addendum records the installed API investigation and Lyo upgrade. The
initial audit header above is retained as historical context; the current POM
target is now Eclipse Lyo `7.0.0.Beta3`.

### 7.1 Environment and scope

- **Installation inspected:** `C:\Program Files\Magic Systems of Systems Architect`
- **Reported product/plugin payload:** 2026x Refresh1
- **Probe scope:** descriptor and JAR inspection, isolated class loading, and
  Java reflection over public generated model interfaces. No application
  process was started and no SysML model was opened, edited, or saved.
- The plugin manager screenshot showed SysML v2 as **Not installed (Available)**.
  Its files are present in the installation tree, which is sufficient to
  inspect the shipped API but does not establish that the plugin is enabled or
  operational in the running application.

### 7.2 Plugin and bundle layout

`plugins/com.nomagic.magicdraw.sysml2/plugin.xml` declares plugin id
`com.dassault_systemes.modeler.magic.sysml`, version `2026x Refresh1`, and a
required plugin `com.nomagic.magicdraw.plugins.impl.sysml` (the SysML v1
plugin). The SysML v2 payload is therefore not an independent host integration
in this installation; its descriptor explicitly depends on the SysML v1
plugin.

The model API is shipped in separate generated-model JARs:

- `com.dassault_systemes.modeler.sysml-…jar` exposes interfaces in
  `com.dassault_systemes.modeler.sysml.model.sysml`.
- `com.dassault_systemes.modeler.kerml-…jar` exposes interfaces in
  `com.dassault_systemes.modeler.kerml.model.kerml`.
- `sysml2.sysml.core` and `sysml2.kerml.core` are integration/service bundles;
  the inspected `SysMLModelingServices` and `KerMLCoreServices` classes expose
  only a static `init()` method. They did not reveal a simple public facade for
  model lookup or editing.

### 7.3 Reflected model surface

The reflected classes are interfaces from the generated model packages. The
probe confirmed the following relevant API:

| Type | Reflected surface | Adapter relevance |
|---|---|---|
| `RequirementUsage` | Extends `ConstraintUsage`; getters include `getRequirementDefinition(): RequirementDefinition`, `getReqId(): String`, `getText(): List<String>`, `getRequiredConstraint(): List<ConstraintUsage>`, `getAssumedConstraint(): List<ConstraintUsage>`, `getSubjectParameter(): Usage`, `getFramedConcern(): List<ConcernUsage>`, `getActorParameter(): List<PartUsage>`, and `getStakeholderParameter(): List<PartUsage>`; also `setReqId(String)`. | Provides a SysML v2 requirement usage and several typed relations useful for an OSLC requirement representation. `getText()` is a list, not a scalar string. |
| `PartUsage` | Extends `ItemUsage`; `getPartDefinition(): List<PartDefinition>`. | A candidate source for a SysML block/part representation, subject to confirming definition/usage semantics in real models. |
| `Usage` | Has `getOwningDefinition()`, `getOwningUsage()`, and `getDefinition()`, plus typed child relations including `getNestedRequirement(): List<RequirementUsage>` and `getNestedPart(): List<PartUsage>`. It also exposes typed nested action, allocation, analysis case, attribute, calculation, case, concern, connection, constraint, enumeration, flow, interface, item, metadata, occurrence, port, reference, rendering, state, transition, use-case, verification-case, view, and viewpoint relations. | Gives typed containment/navigation points. A SysML v2 backend can traverse usages and their relations without the SysML v1 stereotype lookup pattern. |
| KerML `Feature` | Present as an interface in the KerML generated model package and is the common feature type used by SysML v2 usages. | Likely a useful shared abstraction for names, ownership, and feature relationships; its full inherited API and runtime behavior still need a focused probe. |

The probe prints the declared signatures for `RequirementUsage`, `PartUsage`,
and `Usage` in Maven's Surefire output. It loads installation JARs into an
isolated `URLClassLoader`; it does not invoke application services or verify
that concrete model objects can be obtained from the product runtime.

### 7.4 Current adapter implications

The existing MagicDraw repository implementation is SysML v1/UML-oriented: it
uses `com.nomagic.uml2.ext.magicdraw…` model types and SysML stereotype
utilities. Those types and operations do not directly represent the generated
SysML v2/KerML interfaces found in this installation. The API probe did not
change the adapter's repository behavior.

The next implementation step should be a distinct SysML v2 repository backend
or a runtime-selected backend, keeping the v1 implementation intact. Before
mapping complete OSLC resources, a runtime experiment must establish how the
MagicDraw host supplies the active model/resource set and concrete EMF objects,
how model element identifiers and names are obtained, and how proxy/reference
resolution and transactions behave. Then map `RequirementUsage` and
`PartUsage` into the current OSLC resource model and validate their typed
relations against a small real SysML v2 model. This probe alone is not evidence
that those runtime integration points work.

### 7.5 Lyo Beta3 and `Link`

The root project and standalone clients POM now import `lyo-bom` at
`7.0.0.Beta3`; README dependency metadata was updated to match. Lyo's `Link`
class is still available: in Beta3 it is supplied by the managed transitive
artifact `lyo-core-model-7.0.0.Beta3`, reached through `oslc4j-core`. The probe
instantiates `Link(URI, String)` and verifies `getValue()` and `getLabel()`.
This corrects the earlier inspection note that treated the absence of `Link`
from the `oslc4j-core` JAR itself as removal from Lyo.

### 7.6 Kept experiment project and verification

The independent Maven project at
`experiments/magicdraw-sysml-v2-api-probe` contains the repeatable Lyo `Link`
check and the optional installed-bundle reflection tests. It uses the Lyo BOM
and skips the installation-specific checks when neither `-Dmagicdraw.home`
nor `MAGICDRAW_HOME` is supplied. Its README records invocation examples.

Commands and outcomes on this machine (Java 21):

```powershell
mvn -B -f experiments/magicdraw-sysml-v2-api-probe/pom.xml test `
  "-Dmagicdraw.home=C:\Program Files\Magic Systems of Systems Architect"
```

**Passed:** 3 probe tests, 0 failures, 0 errors, 0 skipped. This includes the
Beta3 `Link` check and two installed-bundle tests (descriptor dependency and
generated interface surface).

The probe was also run with `MAGICDRAW_HOME` unset and without
`-Dmagicdraw.home`. **Passed:** 3 tests, 0 failures, 0 errors, 2 skipped; the
Lyo `Link` check still runs, while the two installation-specific tests skip as
designed.

```powershell
mvn -B clean test -DskipITs
```

**Passed:** aggregate build across all five reactor projects; 7 tests,
0 failures, 0 errors, 0 skipped. The optional proprietary MagicDraw repository
module was not part of this standalone reactor test. No MagicDraw process or
authoring integration test was run.

### 7.7 Installed-application runtime verification — 2026-09-29

This section supersedes the final sentence of 7.6: after that API-only review,
the installed product runtime was launched and the checks below were run. The
application process used the product's bundled Java runtime and
`ProjectCommandLine`; this exercised the loaded application/plugin runtime but
did not automate the visible desktop UI.

#### Startup and model loading

- Launched the 2026x Refresh1 product runtime with the installed plugin tree and
  application configuration. The runtime initialized its Swing/AWT and plugin
  services and shut down normally.
- Opened the bundled class-diagram sample as a disposable `.mdzip` copy. The
  application log recorded `ProjectLoadService DONE` for the copied file; a
  separate run of the product's `imagegenerator.exe` loaded the same sample and
  exported four SVG diagrams. The installed SysML v1 and SysML v2 plugins were
  present in the runtime.
- Created a native SysML v2 project from the installed SysML v2 template using
  `SysMLProjectHelper.createUPSProject`. It was editable, had an
  `EsiProjectDescriptor`, and had no UML `primaryModel`. Its `IPrimaryProject`
  exposed an `EsiResourceSet` with writable ESI resources.

#### Editing and transaction behavior

- The application class loader successfully initialized
  `SysMLPackage.eINSTANCE` and `SysMLFactory.eINSTANCE`, created a generated
  `RequirementUsage`, and round-tripped `reqId` through `setReqId`/`getReqId`.
  The generated `getText()` collection was an unmodifiable Ecore list on this
  detached object; the probe did not claim that direct list mutation works.
- On the disposable UML sample copy, the adapter-style `SessionManager`
  transaction probe created a package, closed the session, then created a
  second package and cancelled that session. Both in-memory assertions passed.
  `Project.isEditable()` was false in this Community installation, so the UML
  copy could not be saved and that test does not establish persistent UML save
  behavior.
- On the native SysML v2 project, the probe attached a `RequirementUsage` to a
  writable `EsiResource`. `EsiResource.reset()` removed the uncommitted object.
  A second requirement was committed with `EsiResource.createCommitter().commit`;
  the ESI log recorded a successful resource commit and advanced the client
  revision from 1 to 2.
- The native SysML v2 project was closed and reopened via
  `SysMLProjectHelper.loadUPSProject`. The committed requirement ID was found
  in the reopened ESI resource. The disposable server project was then deleted
  successfully.

#### Limits and code implications

- Runtime verification establishes the project/resource and transaction path
  for the installed SysML v2 plugin. It does not validate visible editor UI
  workflows, semantic placement of a requirement under an authored Namespace,
  or the adapter's complete OSLC mapping against a user-authored model.
- The native SysML v2 project's lack of a UML `primaryModel` confirms that
  `SessionManager`/UML traversal cannot be the v2 backend. The adapter needs to
  resolve ESI resources from the active `IPrimaryProject`, traverse generated
  SysML/KerML EObjects, and use ESI transaction/commit APIs for persistence.
- `experiments/magicdraw-sysml-v2-api-probe/run-magicdraw-transaction-probe.ps1`
  is retained as the repeatable host-runtime harness. Its log assertions
  distinguish the Community read-only UML sample from the successful native
  SysML v2 commit/reopen path.

### 7.8 Repeatability and build results — 2026-09-29

The corrected host-runtime harness completed with exit code 0. Its latest
`msosa.log` markers record the generated API check, ESI rollback, ESI commit,
project close/reopen verification, successful disposable-project deletion,
and `fileSaved=false` for the read-only UML sample copy.

The probe project tests were rerun against the installed product path:

```powershell
mvn -B -f experiments/magicdraw-sysml-v2-api-probe/pom.xml test `
  "-Dmagicdraw.home=C:\Program Files\Magic Systems of Systems Architect"
```

**Passed:** 4 test invocations, 0 failures, 0 errors, 1 skipped. The skipped
case requires the product's ESI runtime service and is exercised by the
application-hosted harness instead. The Lyo Beta3 `Link` test and installed
bundle/API surface tests passed.

```powershell
mvn -B clean test -DskipITs
```

**Passed:** all five default reactor modules; 7 tests, 0 failures, 0 errors,
0 skipped.

An additional attempt to build the optional MagicDraw repository/distribution
modules against this installation (`mvn -B -Pnon-standalone
-Dmagicdraw.installdir="C:\Program Files\Magic Systems of Systems Architect"
test -DskipITs`) stopped during Maven dependency resolution. That module's POM
expects the legacy fixed names `lib/md_api.jar`, `lib/md_common_api.jar`,
`lib/uml2.jar`, and `plugins/com.nomagic.magicdraw.sysml/sysml_api.jar`; none
exist at those paths in the 2026x Refresh1 layout. Maven therefore did not
compile or run tests for the adapter's MagicDraw repository module. The
application-hosted probe used the product's actual versioned runtime JARs and
verified the SysML v2 ESI APIs directly.

### 7.9 2026 Community Edition: SysML v1 .mdzip assessment — 2026-09-30

#### Scope and verified input

This assessment is specifically about continuing the adapter's existing
SysML v1/UML behavior on the installed Magic Systems of Systems Architect
2026x Refresh1 Community Edition. It does not propose replacing the v1 model
with SysML v2. The product build in this installation is
2026.1.0-42-b71a9669.

The earlier class-diagram test established that this runtime can load the
generic UML .mdzip format. It did not establish that a real SysML v1 model or
the adapter's SysML v1 mapping works. To close that gap, the retained
application-hosted probe now opens a disposable copy of the installed
samples/SysML v1/Introduction to SysML v1.mdzip using ProjectCommandLine.
The product log records ProjectLoadService DONE. A traversal through the
loaded model found 14,838 owned elements and 54 diagrams. It found these
SysML v1 stereotype counts:

| SysML v1 stereotype | Count |
|---|---:|
| Block | 131 |
| Requirement | 35 |
| InterfaceBlock | 10 |
| ValueType | 59 |
| PartProperty | 46 |
| ReferenceProperty | 47 |
| ValueProperty | 65 |
| FlowProperty | 20 |
| ItemFlow | 2 |
| ProxyPort | 13 |
| FullPort | 10 |
| AssociationBlock | 0 |

The product log also warned that the sample references a missing external
module named SysML1.3 Interfaces Modeling.mdzip. Loading still completed and
the traversal succeeded, but these counts do not show that every referenced
module was available to the sample.

This confirms that the 2026 product can load and expose a substantial real
SysML v1 .mdzip model, including its SysML profile and attached model
libraries. The probe is deliberately read-only and calls the product model
API directly; it does not instantiate or exercise
SysmlRepositoryMagicDrawImpl. Therefore adapter resource counts, OSLC
serialization, diagram image export for this model, and write operations
remain to be verified after the repository implementation compiles.

#### Compile compatibility findings

A compile-only check used the current repository sources, the installed 2026
runtime JARs, and the already-built adapter API/resource modules. The existing
MagicDraw repository source produced 30 Java compile errors. All 30 cluster
into three removed or changed API families:

| API difference | Compile failures | Existing adapter use | 2026 direction |
|---|---:|---|---|
| SysMLConstants fields such as SYSML_PROFILE, REQUIREMENT_STEREOTYPE, BLOCK_STEREOTYPE, VALUE_TYPE_STEREOTYPE, and VALUE_TYPE_UNIT_TAG are no longer exposed by the 2026 SysMLConstants class. | 10 | ValueType reads and Requirement, Block, and InterfaceBlock creation. | Resolve stereotypes from the project by name through StereotypesHelper, or centralize the stable SysML v1 profile, stereotype, and tag names in adapter-owned constants. Verify profile lookup on real v1 models. |
| Element.getAppliedStereotypeInstance() has been removed. The 2026 UML Element exposes getAppliedStereotype() and hasAppliedStereotype(). | 18 | Flow, value, reference, and part property mapping; linked-stereotype filtering; directed relationship classification. | Use getAppliedStereotype() or StereotypesHelper.hasStereotype for type checks, and StereotypesHelper.getStereotypePropertyFirst for tags. Preserve behavior when multiple stereotypes are applied instead of assuming classifier zero is the only one. |
| ModelHelper.getElementsOfType(Model, Class[], boolean, boolean) is absent from the 2026 ModelHelper. | 2 | Association Block and Information Flow discovery. | Replace the two calls with a traversal over Element.getOwnedElement(), or a supported 2026 query API. The existing implementation already contains recursive package/class traversal that can inform this change. |

The failed compile was against the product JARs, not just the currently
incorrect Maven file paths, so fixing systemPath values alone will not make the
old repository source compile. Conversely, the check did not find compile
breaks in the Project/Application load calls, ProjectsManager,
ProjectDescriptor, SessionManager, ModelElementsManager, ImageExporter, core
UML Element/Model types, or their common diagram and factory calls. The
product's 2026 class layout moves these APIs into versioned JARs: for example,
Application, Project, ProjectsManager, SessionManager, and ImageExporter are
in lib/core-2026.1.0-42-b71a9669.jar, while UML model interfaces and
ElementsFactory are in lib/com.nomagic.magicdraw.uml2-2026.1.0-42-b71a9669.jar.
The 2026 SysML v1 constants class is in
plugins/com.nomagic.requirements/lib/com.nomagic.magicdraw.sysml-2026.1.0-42-b71a9669.jar;
the old Maven dependency path under plugins/com.nomagic.magicdraw.sysml/sysml_api.jar
does not exist.

#### Functionality that must remain covered

The current repository backend is one 4,376-line SysML v1/UML implementation.
Its read mapping covers the model and packages; Blocks and their part,
reference, and value properties; ValueTypes and units; Requirements and their
relations; InterfaceBlocks and FlowProperties; AssociationBlocks; ItemFlows;
connectors, connector ends, ports, and proxy/full ports; and SysML Block
Definition and Internal Block diagrams. It also exports diagram images while
loading/mapping a project. This mapping is based on UML metaclasses,
SysML v1 stereotypes, and MagicDraw-specific presentation APIs; it is not a
generic SysML v2 model traversal.

The backend also implements OSLC-triggered creation or update for packages,
blocks, requirements, item flows, reference/part/value/flow properties,
connectors and connector ends, ports, and interface blocks. Those operations
create UML elements, apply SysML v1 stereotypes, run a MagicDraw session, and
save the .mdzip. Read compatibility alone is not enough to claim that this
write behavior works in Community Edition.

The earlier transaction probe found Project.isEditable() false for a copy of
the installed UML sample, even though the copied file itself was not marked
read-only by Windows. Its session commit/cancel checks were in-memory only;
the sample was not saved. This is evidence about that loaded sample and
runtime, not proof that every Community Edition project is read-only. A
user-authored writable SysML v1 project must be used to determine whether the
Community Edition permits the adapter's save operations. If the product
reports that project as non-editable, full parity for OSLC writes is blocked
by the edition or project permission and the 2026 backend must reject writes
clearly instead of returning success.

#### Version switch design

The repository already has a version-neutral SysmlRepository interface and
MagicDrawManager already instantiates the implementation named by the
sysml.repository.class configuration property. This is the right seam for
preserving old support. Keep the existing SysmlRepositoryMagicDrawImpl source
and its legacy SDK profiles intact; add a separately compiled 2026
implementation/module that also implements SysmlRepository. Select the
implementation and installation with a 2026 Maven profile and the existing
sysml.repository.class setting. The existing Lyo resources and OSLC service
surface can remain shared.

Build one MagicDraw version per adapter distribution. Although much of the
2026 public API keeps the same package and type names, the old and new
proprietary binaries are not safe to mix in one application classloader.
This is a build/deployment switch, not a hot switch inside one running
process. A 2026 Community launch would configure the new implementation class;
an existing 17.x/18.x deployment would continue to configure the old class.

The distribution profile needs a 2026 installation-aware classpath and runtime
layout. The current repository POM expects md_api.jar, md_common_api.jar,
uml2.jar, and sysml_api.jar at old fixed paths. The current distribution WAR
copies root lib JARs and the SysML plugin's main JAR but not the separate
requirements plugin library that now contains SysML v1 API constants. The
SysML v1 plugin descriptor also declares dependencies on other product
plugins. Build and runtime packaging must therefore either use the installed
product/plugin runtime with its plugin directory and launch configuration, or
preserve and include the required versioned plugin dependencies and
configuration. Merely copying four replacement JARs into the current WAR
would not establish a runnable 2026 installation.

#### Recommended implementation and acceptance sequence

1. Add a 2026 profile/module with the installed SDK layout and compile a
   separate SysML v1 backend. Keep the existing 17.x/18.x source and profiles
   unchanged.
2. Adapt the three compile-error families above. Keep these compatibility
   decisions behind the 2026 backend initially; factor shared code only after
   both old and new profiles compile and behavior is compared.
3. Run the new backend on a copy of the real SysML v1 sample and compare the
   OSLC model/resource counts and relationship links with the current backend
   on a supported legacy installation. Check that all 54 diagrams are
   discoverable and that intended images are exported.
4. Test each supported OSLC mutation on a disposable editable .mdzip: mutate,
   commit the session, save, close, reopen, and verify persistence. Also test
   cancel/exception rollback. If Community Edition does not permit saving,
   document the 2026 Community mode as read-only and keep write support for
   the legacy authoring products.
5. Build and run the selected distribution with the configured repository
   class, then smoke-test the OSLC service-provider and resource endpoints.

The retained probe source and runner are
experiments/magicdraw-sysml-v2-api-probe/src/magicdraw/java/edu/gatech/mbsec/adapter/magicdraw/probe/MagicDrawSysmlV1ModelReadProbe.java
and
experiments/magicdraw-sysml-v2-api-probe/run-magicdraw-sysml-v1-read-probe.ps1.
The sample is copied beneath the experiment project's target directory before
loading. The runner completed successfully on 2026-09-30 and recorded the
counts above in the installed product log.

### 7.10 Verification for the mdzip assessment — 2026-09-30

- The SysML v1 application-hosted read probe completed with exit code 0.
  ProjectLoadService DONE was logged for the copied sample; the traversal
  recorded 14,838 model elements, 54 diagrams, and the stereotype counts in
  section 7.9. The installed sample and its target copy have identical SHA-256
  hashes after the read-only run.
- The retained Maven probe project was rerun with the installed product path:
  BUILD SUCCESS, 4 tests, 0 failures, 0 errors, 1 skipped. The skipped
  invocation requires the running ESI application service and is covered by
  the separate application-hosted SysML v2 transaction probe.
- The source compatibility check is intentionally not counted as a passing
  build: javac exited 1 with the 30 compile errors detailed in section 7.9.
  The default five-module reactor build was not rerun because this assessment
  changed no production Java sources.

### 7.11 Separate 2026 backend implementation — 2026-09-30

The version switch now keeps the existing `repo-magicdraw` implementation and
adds `edu.gatech.mbsec.adapter.magicdraw.repo-magicdraw-2026`. Both implement
the shared `SysmlRepository` interface and continue to publish the shared OSLC
resource classes. `MagicDrawManager` already constructs the configured
implementation through `sysml.repository.class`, so no service or resource
class fork was needed.

The `magicdraw-2026x-r1-community` Maven profile selects SDK version
`2026.1.0-42-b71a9669`, the installed Community directory, the new repository
artifact, and a matching distribution runtime classpath. The distribution WAR
contains the 2026 repository JAR and 2026 SysML/Requirements runtime JARs; it
does not contain the legacy repository JAR. The runtime still needs
`sysml.repository.class=edu.gatech.mbsec.adapter.magicdraw.repository.SysmlRepositoryMagicDraw2026Impl`
to select the new implementation. The README now shows the matching launch
command.

The compatibility changes are isolated to the 2026 module: SysML profile
constants are defined locally because the old constants class is gone;
stereotype access uses the 2026 `Element` APIs; recursive collection uses
owned elements because the old `ModelHelper.getElementsOfType` overloads were
removed; URI path identifiers percent-encode spaces, literal percent signs,
and UTF-8 characters; and optional untyped properties no longer abort the
entire map. Diagram image and mapping-log output paths can be configured for
the selected runtime.

The backend exposes `loadSysMLProjectFromLoadedProject(projectId, project)`
for product hosts that already loaded a project. It shares the regular mapping
pipeline but avoids calling `Application.start()` a second time. The retained
probe uses MagicDraw's `ProjectCommandLine` host, not a plain Java main, to
exercise this entry point.

### 7.12 2026 Community backend verification — 2026-09-30

- `experiments/magicdraw-sysml-v2-api-probe/run-magicdraw-sysml-v1-backend-probe.ps1`
  copied the installed `Introduction to SysML v1.mdzip` to the experiment
  target and launched the backend inside the installed 2026x Refresh1 product
  host. The repository returned success with 1 model, 122 blocks, 35
  requirements, 10 interface blocks, 47 value types, 43 part properties, 41
  reference properties, 42 value properties, 14 flow properties, 1 item flow,
  13 proxy ports, 10 full ports, 23 Block Definition diagrams, and 5 Internal
  Block diagrams.
- The probe asserted that the project, model, blocks, and both diagram types
  were mapped. It canceled its temporary MagicDraw session and did not save or
  alter the installed sample. It exported diagram images and the mapping log
  under the ignored experiment `target` directory; no writes were directed to
  the product installation. The installed sample and disposable copy retained
  the same SHA-256 hash (`B08E325691D0BFC05650FC57E13D7917B9D778847A162751C818AA911BBC15C5`).
- `mvn -B test -DskipITs` passed: 7 tests, 0 failures, 0 errors, 0 skipped.
- `mvn -B -Pmagicdraw-2026x-r1-community -pl edu.gatech.mbsec.adapter.magicdraw.distribution -am -DskipTests package`
  passed. The resulting WAR inspection confirmed that the 2026 repository JAR
  is present and the legacy repository JAR is absent.
- This validates SysML v1 model loading and OSLC repository mapping inside the
  installed Community host. It does not validate the assembled WAR as a live
  HTTP service, or OSLC-driven edit/save/reopen behavior. The earlier
  transaction probe found the installed sample was not editable, so mutation
  persistence still needs a user-authored writable `.mdzip` before claiming
  Community write support.

The two diagram image directories use diagram names as filenames, matching the
existing behavior. In this sample, multiple diagrams share a name, so later
exports overwrite earlier images in the flat directories even though all 23
Block Definition and 5 Internal Block diagrams were mapped. The OSLC diagram
resource counts above are unaffected.

### 7.13 Integration tests without `skipITs` — 2026-09-30

The 2026 repository module now has a Failsafe integration test bound to the
`integration-test` and `verify` phases of the
`magicdraw-2026x-r1-community` profile. It starts the installed product's
bundled Java runtime, loads a disposable copy of the installed SysML v1 sample,
and runs `SysmlRepositoryMagicDraw2026Impl` against the loaded project. It
asserts that the backend registers a model, blocks, Block Definition diagrams,
and Internal Block diagrams. A result file is written only after those
assertions pass; it and the product console log are retained under the module's
`target/magicdraw-2026-it` directory.

The product-hosted test command completed successfully without setting
`skipITs` or `skipTests`:

```text
mvn -B -Pmagicdraw-2026x-r1-community -pl :oslc4j-magicdraw-repo-magicdraw-2026 -am verify
```

Failsafe reported 1 test, 0 failures, 0 errors, and 0 skipped. The result was
1 model, 122 blocks, 35 requirements, 10 interface blocks, 47 value types, 43
part properties, 41 reference properties, 42 value properties, 14 flow
properties, 1 item flow, 13 proxy ports, 10 full ports, 23 Block Definition
diagrams, and 5 Internal Block diagrams. The 2026 sample copy is isolated under
`target`; the installed sample is opened read-only and is not changed.

The existing `acceptance` profile continues to run the OSLC HTTP tests against
Jetty with the standalone fixture. Its command also completed successfully
without `skipITs` or `skipTests`:

```text
mvn -B -Pacceptance verify
```

Surefire reported 7 unit tests and Failsafe reported 4 HTTP integration tests;
both suites had 0 failures, 0 errors, and 0 skipped. These HTTP tests verify the
catalog, service provider, query capability, block query, and individual block
resource. They do not use the MagicDraw repository implementation. The new
product-hosted test verifies the installed 2026 SDK and repository mapping; it
does not start the HTTP server. This leaves live HTTP integration with the 2026
backend and edit/save/reopen behavior on an editable Community project for
future testing.

The first harness run initially failed because the product redirects
`System.out` to its own application log, rather than Maven's process stream.
The integration test was changed to assert a success result file created by
the probe after mapping assertions. The rerun then passed as reported above.
