<h1 align="center" style="font-weight: normal;"><b>MC-Runtime-Test</b></h1>
<p align="center">Run the Minecraft client inside your CI/CD pipeline.</p>
<p align="center">
MC-Runtime-Test | <a href="https://github.com/3arthqu4ke/headlessmc">HMC</a> | <a href="https://github.com/3arthqu4ke/hmc-specifics">HMC-Specifics</a> | <a href="https://github.com/3arthqu4ke/hmc-optimizations">HMC-Optimizations</a>
</p>

<div align="center">

[![CodeFactor](https://www.codefactor.io/repository/github/headlesshq/mc-runtime-test/badge/main)](https://www.codefactor.io/repository/github/headlesshq/mc-runtime-test/overview/main)
[![GitHub All Releases](https://img.shields.io/github/downloads/headlesshq/mc-runtime-test/total.svg)](https://github.com/headlesshq/mc-runtime-test/releases)
![GitHub License](https://img.shields.io/github/license/headlesshq/mc-runtime-test)
![GitHub Last Commit](https://img.shields.io/github/last-commit/headlesshq/mc-runtime-test)

</div>

> [!NOTE]  
> This is **not an official Minecraft product**. It is **not approved by or associated with Mojang or Microsoft**.

---

MC-Runtime-Test enables you to run the Minecraft client within your CI/CD pipelines, simplifying the testing of runtime bugs in Minecraft mods.
Manual testing for different Minecraft versions and modloaders can be time-consuming, especially when bugs occur only in runtime environments launched via a Minecraft launcher.
This project helps streamline that process by automating the client launch and basic test execution.

## Features
- Utilizes [HeadlessMC](https://github.com/3arthqu4ke/headlessmc) for headless Minecraft launches.
- Employs Xvfb for virtual framebuffer support.
- Includes a lightweight mod that:
  - Join a single-player world.
  - Wait for chunks to load.
  - Quit the game after a few seconds.
- Supports Minecraft’s [GameTest Framework](https://www.minecraft.net/en-us/creator/article/get-started-gametest-framework) to run registered tests for newer versions.

### Supported Minecraft Versions and Modloaders
| Version         | Forge           | Fabric          | NeoForge        |
|-----------------|----------------|----------------|----------------|
| 1.21 - 1.21.10  | ✔️              | ✔️              | ✔️              |
| 1.20.2 - 1.20.6 | ✔️              | ✔️              | ✔️              |
| 1.20.1          | ✔️              | ✔️              | ⚠️              |
| 1.19 - 1.19.4   | ✔️              | ✔️              | —              |
| 1.18.2          | ✔️              | ✔️              | —              |
| 1.17.1          | ✔️              | ✔️              | —              |
| 1.16.5          | ✔️              | ✔️              | —              |
| 1.12.2          | ✔️              | ⚠️              | —              |
| 1.8.9           | ✔️              | ⚠️              | —              |
| 1.7.10          | ✔️              | ⚠️              | —              |

*⚠️ Versions marked with a warning symbol have limited or untested support.*

---

## Quickstart Example
Below is a basic workflow example to run the Minecraft client using MC-Runtime-Test.

<pre lang="yml">
---
name: Run Minecraft Client

on:
  workflow_dispatch:

env:
  java_version: 21

jobs:
  run:
    runs-on: ubuntu-latest
    steps:
      - name: Install Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.java_version }}
          distribution: "temurin"

      - name: [Example] Build mod
        run: ./gradlew build

      - name: [Example] Stage mod for test client
        run: |
          mkdir -p run/mods
          cp build/libs/&lt;your-mod&gt;.jar run/mods

      - name: Run MC test client
        uses: headlesshq/mc-runtime-test@4.1.0 <!-- x-release-please-version -->
        with:
          mc: 1.21.5
          modloader: fabric
          regex: .*fabric.*
          mc-runtime-test: fabric
          java: ${{ env.java_version }}
</pre>

More examples:
- [Fabric Workflow Example](https://github.com/3arthqu4ke/hmc-optimizations/blob/1.20.4/.github/workflows/run-fabric.yml)
- [Matrix Workflow Testing Multiple Versions](https://github.com/3arthqu4ke/hmc-specifics/blob/main/.github/workflows/run-matrix.yml)

---

## Inputs
The following table summarizes the available inputs for customization:

| Input                 | Description                                               | Required | Example                                  |
|-----------------------|-----------------------------------------------------------|----------|------------------------------------------|
| `mc`                  | Minecraft version to run                                  | Yes      | `1.20.4`                                 |
| `modloader`           | Modloader to install                                      | Yes      | `forge`, `neoforge`, `fabric`            |
| `regex`               | Regex to match the modloader jar                          | Yes      | `.*fabric.*`                             |
| `java`                | Java version to use                                       | Yes      | `8`, `16`, `17`, `21`                    |
| `mc-runtime-test`     | MC-Runtime-Test jar to download                           | Yes      | `none`, `lexforge`, `neoforge`, `fabric` |
| `dummy-assets`        | Use dummy assets during testing                           |          | `true`, `false`                          |
| `xvfb`                | Runs the game with Xvfb                                   |          | `true`, `false`                          |
| `headlessmc-command`  | Command-line arguments for HeadlessMC                     |          | `--jvm "-Djava.awt.headless=true"`       |
| `fabric-api`          | Fabric API version to download or none                    |          | `0.97.0`, `none`                         |
| `fabric-gametest-api` | Fabric GameTest API version or none                       |          | `1.3.5+85d85a934f`, `none`               |
| `download-hmc`        | Download HeadlessMC                                       |          | `true`, `false`                          |
| `hmc-version`         | HeadlessMC version                                        |          | `2.7.1`, `1.5.0`                         |
| `cache-mc`            | Cache `.minecraft` <br/>(`true` defaults to `blacksmith`) |          | `github`, `blacksmith`, `true`, `false`  |

---

## Caching
MC-Runtime-Test optionally caches `.minecraft` to improve execution time.
By default `cache-mc` is set to `github`, which uses `actions/cache`.
Set `cache-mc` to `false` to disable caching.

Another option is `blacksmith` for `blacksmith/cache`. 
Simply follow the instructions [here](https://docs.blacksmith.sh/introduction/quickstart)
to enable blacksmith for your repositories and enable the `cache-mc` input.

## Running Your Own Tests
MC-Runtime-Test supports Minecraft’s [Game-Test Framework](https://www.minecraft.net/en-us/creator/article/get-started-gametest-framework). It executes `/test runall` upon joining a world.

> [!TIP]  
> Currently, Forge and NeoForge GameTest discovery may require additional setup, [hacks](gametest/src/main/java/me/earth/clientgametest/mixin/MixinGameTestRegistry.java), or other modifications to register structure templates correctly. We expect to simplify this for future releases.

You can also use the `headlessmc-command` input to specify a JVM argument to enforce the minimum number of GameTests you expect to be executed:

<pre lang="bash">
-DMcRuntimeGameTestMinExpectedGameTests=1
</pre>

---

## Acknowledgments
Special thanks to [wagyourtail](https://github.com/wagyourtail) for the [unimined](https://github.com/unimined/unimined) Gradle plugin, which enabled multi-modloader support and accelerated development of this project.

---
