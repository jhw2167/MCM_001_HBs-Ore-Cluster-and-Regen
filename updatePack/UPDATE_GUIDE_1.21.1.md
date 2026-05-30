# Multiloader Mod Update Guide: 1.20.1 → 1.21.1 (Fabric + Common)

This guide documents every change required to update a multiloader Minecraft mod from 1.20.1 (Fabric/Forge) to 1.21.1 (Fabric, with NeoForge to be handled separately). It assumes the mod depends on Balm and HBs_Foundation, uses the `multiloader-common` and `multiloader-loader` convention plugins from `buildSrc`, and follows the project structure: root build.gradle, Common subproject, Fabric subproject, with a shared gradle.properties.

## Critical Principle: Overwrite, Don't Merge

The build.gradle files included in the `updatePack` directory must **overwrite** the existing build.gradle files in each respective subproject. Do not attempt to merge old and new build.gradle files — the build system structure has fundamentally changed (VanillaGradle → NeoForge ModDev, manual wiring → convention plugins).

Only two things from the previous build.gradle files must be preserved:

1. **Repositories** — any custom Maven repositories from the old root build.gradle must be copied into the new root build.gradle's `subprojects` block (e.g. Twelve Iterations for Balm, BlameJared for JEI).
2. **Subproject-specific dependencies** — each subproject's unique dependencies (e.g. `hbs_foundation`, mod-specific libs) must be added to the new build.gradle's `dependencies` block.

Everything else (plugins, Java toolchain, jar manifest, processResources, publishing, source wiring) is handled by the convention plugins and the new build.gradle templates.

---

## 1. Copy buildSrc from HBs_Foundation

The project requires the `multiloader-common` and `multiloader-loader` convention plugins. Copy the entire `buildSrc/` directory from HBs_Foundation into the target mod's root. This provides:

- `multiloader-common.gradle` — handles java-library, maven-publish, Java toolchain, jar manifest, processResources expansion, publishing, and outgoing capability declarations.
- `multiloader-loader.gradle` — applies `multiloader-common`, then wires Common's source and resources into the loader subproject via `commonJava`/`commonResources` configurations.

Since the convention plugins handle processResources, jar configuration, publishing, and source wiring, the root and subproject build.gradle files become much simpler.

---

## 2. Gradle Wrapper (gradle/wrapper/gradle-wrapper.properties)

Update the Gradle distribution to 8.10:

```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
```

---

## 3. gradle.properties

### Version updates (replace old values):

```properties
# Common
minecraft_version=1.21.1
java_version=21

# Forge (legacy - keep defined so processResources expansion doesn't fail)
forge_version=47.3.0
forge_loader_version_range=[47,)
forge_version_range=[47,)
minecraft_version_range=[1.21.1, 1.22)

# NeoForge (needed by Common's NeoForm even if not building a NeoForge subproject yet)
neoforge_version=21.1.8
neoforge_version_range=[21-beta,)
neoforge_loader_version_range=[1,)
neo_form_version=1.21.1-20240808.144430
parchment_minecraft=1.21
parchment_version=2024.06.23

# Fabric
fabric_version=0.116.7+1.21.1
fabric_loader_version=0.17.3
loom_version=1.8-SNAPSHOT

# Dependencies
balm_version=21.0.55+1.21.1
balm_version_range=[21.0.39,)
foundation_version=1.7.0-SNAPSHOT
```

### New properties to add:

- **`credits`** — required by the `multiloader-common` convention plugin's processResources expandProps. Add it under Mod options:
  ```properties
  credits=Holy_Buckets
  ```
- **`neo_form_version`**, **`parchment_minecraft`**, **`parchment_version`** — required by Common's NeoForge ModDev configuration.
- **All NeoForge properties** (`neoforge_version`, `neoforge_version_range`, `neoforge_loader_version_range`) — required by the convention plugin's processResources even if you're not building a NeoForge subproject yet. Without them, the expansion will fail with a missing property error.

### Key points:
- Remove any commented-out old `balm_version` lines.
- The `version` property should NOT be in gradle.properties. The root build.gradle sets `version = mod_version` to derive the project version from `mod_version`. Having both causes conflicts.

---

## 4. settings.gradle

### Plugin repositories — replace old Forge maven with NeoForge:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        maven {
            name = 'NeoForge'
            url = 'https://maven.neoforged.net/releases'
        }
        maven {
            name = 'Sponge Snapshots'
            url = 'https://repo.spongepowered.org/repository/maven-public/'
        }
    }
}
```

### Subproject includes — comment out forge:

```groovy
include("common")
include("fabric")
//include("forge") // Forge not available for 1.21.1 - replaced by NeoForge (to be configured separately)
```

---

## 5. Root build.gradle — Overwrite Entirely

Replace the entire root build.gradle. The convention plugins now handle most of what the old subprojects block did. The root file only needs to:

1. Declare the top-level plugins (Fabric Loom and NeoForge ModDev, both `apply(false)`).
2. Set `version = mod_version`.
3. Configure allprojects Javadoc settings.
4. In the subprojects block: declare shared repositories and shared dependencies.

**The new root build.gradle:**

```groovy
plugins {
    // see https://fabricmc.net/develop/ for new versions
    id 'fabric-loom' version "${loom_version}" apply(false)
    // see https://projects.neoforged.net/neoforged/moddevgradle for new versions
    id 'net.neoforged.moddev' version '2.0.49-beta' apply(false)
}

version = mod_version

allprojects {
    apply plugin: "idea"

    tasks.withType(Javadoc).configureEach {
        options.addStringOption('Xdoclint:none', '-quiet')
        options.encoding = 'UTF-8'
    }
}

subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        // ADD YOUR PROJECT'S CUSTOM REPOSITORIES HERE
        // Copy any custom Maven repos from the old root build.gradle.
        // Common repos needed by most HB mods:
        maven {
            url "https://maven.twelveiterations.com/repository/maven-public/"
            content {
                includeGroup "net.blay09.mods"
            }
        }
    }

    plugins.withType(JavaPlugin) {
        dependencies {
            implementation "com.google.code.gson:gson:${gson_version}"
            implementation "org.antlr:antlr4-runtime:${antlr_version}"
            implementation "org.xerial:sqlite-jdbc:${sqlite_version}"
        }
    }

    tasks.withType(JavaCompile).configureEach {
        it.options.encoding = 'UTF-8'
    }
}
```

### What was removed vs. the old root build.gradle:
- **Plugins:** ForgeGradle, VanillaGradle, and Mixin plugins are gone. Replaced by `fabric-loom` and `net.neoforged.moddev`.
- **`plugins.withType(JavaPlugin)` block:** No longer configures jar manifest, sourcesJar, Java toolchain, or `withSourcesJar()`/`withJavadocJar()` — the `multiloader-common` convention plugin handles all of this.
- **`processResources` block:** Entirely removed from root. The `multiloader-common` convention plugin handles resource expansion with its own expandProps map.
- **Shared dependencies:** Only truly shared deps (gson, antlr, sqlite) remain. Balm and foundation are declared per-subproject.

### Why `mavenLocal()` matters:
HBs_Foundation is published to the local Maven repository (`~/.m2/repository`) via `publishToMavenLocal`. Without `mavenLocal()` in the repositories block, Gradle cannot find it. If dependency resolution fails with "Could not find com.holybuckets.foundation:...", confirm that `mavenLocal()` is present and that you ran `publishToMavenLocal` on the foundation project.

---

## 6. Common/build.gradle — Overwrite with Template

Overwrite `Common/build.gradle` with the template from `updatePack/common-build.gradle`, then add the mod's specific dependencies.

**The new Common/build.gradle:**

```groovy
plugins {
    id 'multiloader-common'
    id 'net.neoforged.moddev'
}

neoForge {
    neoFormVersion = neo_form_version
    // Automatically enable AccessTransformers if the file exists
    def at = file('src/main/resources/META-INF/accesstransformer.cfg')
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    parchment {
        minecraftVersion = parchment_minecraft
        mappingsVersion = parchment_version
    }
}

dependencies {
    compileOnly group: 'org.spongepowered', name: 'mixin', version: '0.8.5'
    implementation group: 'com.google.code.findbugs', name: 'jsr305', version: '3.0.1'

    implementation("net.blay09.mods:balm-common:${balm_version}") {
        changing = true
    }

    // ADD YOUR MOD'S COMMON DEPENDENCIES HERE
    // Example for a mod that depends on HBs_Foundation:
    implementation("com.holybuckets.foundation:hbs_foundation-common-${minecraft_version}:${foundation_version}") {
        changing = true
    }
}

configurations {
    commonJava {
        canBeResolved = false
        canBeConsumed = true
    }
    commonResources {
        canBeResolved = false
        canBeConsumed = true
    }
}

artifacts {
    commonJava sourceSets.main.java.sourceDirectories.singleFile
    commonResources sourceSets.main.resources.sourceDirectories.singleFile
}
```

### What changed:
- **`multiloader-common` replaces `idea`, `java`, `maven-publish`** — the convention plugin applies all three plus configures Java toolchain, jar manifest, processResources, publishing, and capabilities.
- **`net.neoforged.moddev` replaces `org.spongepowered.gradle.vanilla`** — NeoForm (via ModDev) is the new tool for deobfuscating Minecraft sources. This is build tooling, not mod loader support.
- **No `base {}` block** — the convention plugin sets `archivesName` using `mod_id`, `project.name`, and `minecraft_version`.
- **No `publishing {}` block** — the convention plugin configures publishing.
- **`commonJava`/`commonResources` configurations** — these expose Common's sources to loader subprojects via the `multiloader-loader` plugin.

---

## 7. Fabric/build.gradle — Overwrite with Template

Overwrite `Fabric/build.gradle` with the template from `updatePack/fabric-build.gradle`, then add the mod's specific dependencies.

**The new Fabric/build.gradle:**

```groovy
plugins {
    id 'multiloader-loader'
    id 'fabric-loom'
}

version = "${mod_version}"

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${parchment_minecraft}:${parchment_version}@zip")
    }
    modImplementation "net.fabricmc:fabric-loader:${fabric_loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"

    modImplementation("net.blay09.mods:balm-fabric:${balm_version}") {
        transitive = false
    }

    // ADD YOUR MOD'S FABRIC DEPENDENCIES HERE
    // Example for a mod that depends on HBs_Foundation:
    modImplementation("com.holybuckets.foundation:hbs_foundation-fabric-${minecraft_version}:${foundation_version}") {
        transitive = false
        changing = true
    }

    implementation group: 'com.google.code.findbugs', name: 'jsr305', version: '3.0.1'
}

loom {
    def aw = project(':common').file("src/main/resources/${mod_id}.accesswidener")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
    mixin {
        defaultRefmapName.set("${mod_id}.refmap.json")
    }
    runs {
        client {
            client()
            setConfigName('Fabric Client')
            ideConfigGenerated(true)
            runDir('runs/client')
        }
        server {
            server()
            setConfigName('Fabric Server')
            ideConfigGenerated(true)
            runDir('runs/server')
        }
    }
}
```

### What changed:
- **`multiloader-loader` replaces manual Common wiring** — the old `implementation project(":common")`, `source(project(":common").sourceSets.main.allSource)`, `from project(":common").sourceSets.main.resources`, etc. are all gone. The convention plugin handles all cross-project source/resource wiring via `commonJava`/`commonResources` configurations.
- **No `publishing {}` block** — handled by the convention plugin.
- **No `tasks.withType(JavaCompile)` source inclusion** — handled by the convention plugin.
- **No `tasks.named("sourcesJar")` from common** — handled by the convention plugin.
- **Parchment mappings** — `loom.layered` with Parchment overlay replaces plain `loom.officialMojangMappings()`.
- **Run directories** — separated into `runs/client` and `runs/server` instead of a shared `run/` directory.

---

## 8. Dependency Naming Convention Change (Important)

The HBs_Foundation artifacts were renamed from PascalCase to lowercase in the 1.21.1 release. Every reference to the foundation dependency must be updated:

| Context | Old artifact ID | New artifact ID |
|---------|----------------|-----------------|
| Common  | `HBs_Foundation-common-${minecraft_version}` | `hbs_foundation-common-${minecraft_version}` |
| Fabric  | `HBs_Foundation-fabric-${minecraft_version}` | `hbs_foundation-fabric-${minecraft_version}` |
| NeoForge (future) | `HBs_Foundation-neoforge-${minecraft_version}` | `hbs_foundation-neoforge-${minecraft_version}` |

The group ID (`com.holybuckets.foundation`) and version property (`foundation_version`) remain unchanged.

---

## 9. Common Errors and Fixes

### "Could not find method processResources()" on Common
The old `processResources { ... }` call in the root build.gradle fails because NeoForge ModDev does not register that task the same way during evaluation. **Fix:** The `multiloader-common` convention plugin handles processResources. Remove any `processResources` or `tasks.withType(ProcessResources)` configuration from the root build.gradle entirely.

### "Cannot resolve project :common" from Fabric
This happens when Fabric's build.gradle uses `implementation project(":common")` directly. Common uses NeoForge ModDev (NeoForm mappings) and Fabric uses Loom (Mojang mappings) — the compiled artifacts are incompatible. **Fix:** Use `multiloader-loader` convention plugin instead of manual project wiring. The convention plugin consumes Common's raw source via `commonJava`/`commonResources` configurations and recompiles it under Loom's mappings.

### HBs_Foundation "version is unspecified"
Fabric reports a dependency's version as unspecified when that mod's own `fabric.mod.json` has an unexpanded `${version}` placeholder. **Fix:** Ensure the foundation project's processResources expandProps map includes a `"version"` key (or `"mod_version"` if you've standardized on that), and that the `fabric.mod.json` template uses the matching placeholder. The property must be defined somewhere Gradle can resolve it — either in `gradle.properties` or set programmatically in build.gradle (e.g. `version = mod_version`).

### fabric-resource-loader-v0 mixin crash (LanguageMixin injection failure)
Fabric API's `fabric-resource-loader-v0` sub-module version 2.0.0 is incompatible with MC 1.21.1. This happens when Fabric Loom resolves an incorrect version of this sub-module. **Fix:** Ensure `loom_version` is set to `1.8-SNAPSHOT` (or the version recommended at [fabricmc.net/develop](https://fabricmc.net/develop/)). Stale or mismatched Loom snapshots can decompose the Fabric API BOM into wrong sub-module versions. After changing Loom version, run a clean build (`./gradlew clean build`).

### "Could not find com.holybuckets.foundation:hbs_foundation-..." dependency
The foundation jar exists in `~/.m2/repository` but Gradle can't find it. **Fix:** Ensure `mavenLocal()` is in the repositories block (either in root build.gradle's `subprojects {}` or in the convention plugin). Then confirm the foundation project was published with `./gradlew publishToMavenLocal` (not just a jar copy — Gradle needs the `.pom` and `maven-metadata.xml` files).

### Mixin registered in both "mixins" and "client" arrays
If a mixin targets a client-only class (like `Minecraft.class`), it must only appear in the `"client"` array of the mixins JSON, not in `"mixins"` (which applies on both sides). Having it in both causes duplicate registration and can prevent the mixin from applying. **Fix:** Remove the mixin from the `"mixins"` array and keep it only in `"client"`.

---

## 10. Checklist

When updating a mod, work through these steps in order:

1. **`buildSrc/`** — copy from HBs_Foundation (multiloader-common, multiloader-loader convention plugins)
2. **`gradle/wrapper/gradle-wrapper.properties`** — Gradle 8.10
3. **`gradle.properties`** — all version numbers; add NeoForge/NeoForm/Parchment properties; add `credits`; ensure `version` is NOT defined (use `mod_version` only)
4. **`settings.gradle`** — swap Forge repo for NeoForge repo, comment out forge subproject
5. **`build.gradle` (root)** — overwrite with template; add custom repositories from old file
6. **`Common/build.gradle`** — overwrite with template; add mod-specific dependencies (foundation, etc.)
7. **`Fabric/build.gradle`** — overwrite with template; add mod-specific dependencies (foundation, etc.); lowercase artifact names
8. **Verify** no files in Forge/ or NeoForge/ directories were modified (handle NeoForge migration separately)
9. **`./gradlew clean build`** — confirm compilation succeeds
10. **`./gradlew :fabric:runServer`** — confirm server starts without mixin errors