# MagicDraw SysML v2 API probe

This standalone Java 21 test project records the API surface shipped with the
local Magic Systems of Systems Architect installation. It does not start the
application, open a model, or require a MagicDraw license. SysML v2 tests are
skipped when no installation path is supplied.

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
method signatures. The installed-bundle checks are diagnostics for this
2026x Refresh 1 payload, not an assertion that the plugin is active in the UI.
