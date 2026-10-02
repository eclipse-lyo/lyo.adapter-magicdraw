# MagicDraw SysML API and runtime probes

This Java 21 test project records the API surface shipped with the local Magic
Systems of Systems Architect installation. Maven tests inspect the installed
bundles without starting the application. SysML v2 bundle tests are skipped
when no installation path is supplied.

Run the Lyo Beta3 check on any machine:

```powershell
mvn -f experiments/magicdraw-sysml-v2-api-probe/pom.xml test
```

Run the installed SysML v2 bundle and Java reflection experiments on Windows:

```powershell
mvn -f experiments/magicdraw-sysml-v2-api-probe/pom.xml test `
  "-Dmagicdraw.home=C:\Program Files\Magic Systems of Systems Architect"
```

The tests inspect the SysML v2 plugin descriptor and model JARs, load generated
SysML v2/KerML interfaces in an isolated class loader, and print their public
method signatures. Standalone EMF object creation is skipped when the installed
model needs the application's EsiConfig service.

## Application-hosted project tests

The reactor has a product-hosted Failsafe integration test for the selectable
SysML v1 repository backend. Run it with the installed 2026 Community edition:

```powershell
mvn -B -Pmagicdraw-2026x-r1-community `
  -pl :oslc4j-magicdraw-repo-magicdraw-2026 -am verify
```

This `verify` run does not set `skipITs`. Failsafe launches the installation's
bundled Java runtime, opens a disposable copy of the installed SysML v1 sample,
and asserts the 2026 backend maps models, blocks, and both diagram types. It
then starts the adapter's JAX-RS application with that repository in an
embedded Jetty server on an ephemeral port. The test performs read-only GETs
against the catalog, service provider, blocks query, and one block resource.
It does not call any create, update, or delete route, and checks that the
installed sample and disposable model copy retain their original SHA-256
hashes. The test writes its mapping result and product log under
`edu.gatech.mbsec.adapter.magicdraw.repo-magicdraw-2026/target/magicdraw-2026-it`.
Override the installation selected by the profile with
`-Dmagicdraw.installdir="C:\\path\\to\\Magic Systems of Systems Architect"`.

The adapter HTTP acceptance tests remain available separately, using the
standalone repository fixture so they do not require MagicDraw:

```powershell
mvn -B -Pacceptance verify
```

They exercise the catalog, service provider, query capability, block query, and
individual block resource over HTTP using the standalone repository fixture.
The product-hosted test also exercises the same GET routes with the 2026
repository implementation inside the product process.

### Manual runtime probes

On the installed Windows workstation, run:

```powershell
experiments/magicdraw-sysml-v2-api-probe/run-magicdraw-transaction-probe.ps1
```

The script uses the bundled application Java runtime and a copy of the bundled
class-diagram sample. It launches the product runtime through
`ProjectCommandLine`, opens the sample, closes a session containing one package
edit, and cancels a second session. In the Community installation tested here,
the bundled UML sample reports `Project.isEditable() == false`; the edits pass
their in-memory assertions but the sample copy cannot be saved.

The script also creates a disposable native SysML v2 project from the installed
SysML v2 template. It creates a `RequirementUsage` with the installed generated
factory, adds it to a writable `EsiResource`, verifies `reset()` rolls back an
uncommitted object, commits a second requirement through the ESI resource API,
closes and reopens the project through `SysMLProjectHelper`, then verifies the
committed requirement survived. The disposable repository project is deleted
after verification. The script checks the application log for success markers;
the product redirects command-line application output to `msosa.log`.

To open an actual SysML v1 sample and count its v1 elements and diagrams, run:

    experiments/magicdraw-sysml-v2-api-probe/run-magicdraw-sysml-v1-read-probe.ps1

This read-only probe uses a copy of the installed “Introduction to SysML v1”
sample and records recognized SysML v1 stereotype counts in the application
log. It verifies product-level sample loading and traversal; it does not invoke
the OSLC adapter repository implementation.
The product log did warn about one unresolved external module reference, so
the probe does not establish complete resolution of all model dependencies.

The product's sample models and SysML v2 template are never edited. The UML
and SysML v1 sample copies and application probe classes stay under `target/`. The native
SysML v2 test project is created in the installed local Teamwork Cloud service
and removed after the reopen check. The probe deliberately uses the host's ESI
resource API: the SysML v2 EMF collections are ESI-managed and ordinary detached
EMF/XMI persistence is not a valid substitute for a native project transaction.
