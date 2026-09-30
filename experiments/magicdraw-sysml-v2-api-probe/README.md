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

The product's sample models and SysML v2 template are never edited. The UML
and SysML v1 sample copies and application probe classes stay under `target/`. The native
SysML v2 test project is created in the installed local Teamwork Cloud service
and removed after the reopen check. The probe deliberately uses the host's ESI
resource API: the SysML v2 EMF collections are ESI-managed and ordinary detached
EMF/XMI persistence is not a valid substitute for a native project transaction.
