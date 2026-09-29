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
