# Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for your game version and
   [Fabric API](https://modrinth.com/mod/fabric-api) for your game version.
2. Install [Flashback](https://modrinth.com/mod/flashback) (0.39+, 0.42+ on 26.x).
3. Choose the matching JAR on [Downloads](Guide-downloads) and put it into `mods`.
4. Java 21 runs the 1.21.x jars; the 26.x jars need Java 25.

| Minecraft | Reference Flashback build | Java | Jar family |
|---|---|---|---|
| 1.21, 1.21.1 | 0.39.7 | 21 | 1.21.1 |
| 1.21.4 | 0.39.7 | 21 | 1.21.4 |
| 1.21.5 | 0.39.7 | 21 | 1.21.5 |
| 1.21.6 to 1.21.8 | 0.39.7 | 21 | 1.21.8 |
| 1.21.9, 1.21.10 | 0.39.7 | 21 | 1.21.10 |
| 1.21.11 | 0.39.7 | 21 | 1.21.11 |
| 26.1 to 26.1.2 | 0.42.1 | 25 | 26.1 |
| 26.2 | 0.43.2 | 25 | 26.2 |
| 26.3 | 0.43.6 | 25 | 26.3 |

## Match the files before launching

The table lists the Flashback builds used as compatibility references in this checkout. Match Minecraft, Fabric API, Flashback and the Actors JAR to the same version family. Minecraft 26.1 is tested on 26.1.2.

The 0.11.1 package is a release candidate until it is published. Do not assume a new build is available on GitHub Releases before it appears there.

## Open the editor

Launch Minecraft, open a Flashback replay and look for **Actors** in the editor menu. Start with [Record a take](Guide-takes).

If the menu is missing, confirm the Actors and Flashback JARs are installed in the instance you actually launched. See [troubleshooting](Guide-troubleshooting).

