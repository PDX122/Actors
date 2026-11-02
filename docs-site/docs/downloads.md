# Downloads

Choose the Actors build for your Minecraft version. Install it alongside matching versions of Flashback and Fabric API.

## Version builds

The current **0.11.1** builds are a **release candidate**. These are the packaged JARs from the validated release folder, not a newly published stable release. Use Java 21 for 1.21.x or Java 25 for 26.x.

{{download_catalog}}

## All versions

The ZIP contains all nine version-specific JARs, checksums and the saved QA summary. Install only the JAR for the Minecraft instance you intend to launch.

{{download_bundles}}

## Verify your download

Each version row includes its SHA-256 checksum. You can also download the complete checksums file with the bundle above.

On Windows, compare the file's hash using PowerShell:

```powershell
Get-FileHash .\flashback-actors-26.3-0.11.1.jar -Algorithm SHA256
```

The [install guide](install.md) explains the required dependencies. Read the [changelog](changelog.md) for changes and the [test log](test-log/) for the saved validation and its limits.

Earlier published mod versions remain available on [GitHub Releases](https://github.com/PDX122/Actors/releases).
