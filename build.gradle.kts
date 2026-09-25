import dev.architectury.pack200.java.Pack200
import net.fabricmc.loom.configuration.providers.forge.fg2.Pack200Provider

plugins {
    java
    id("gg.essential.loom") version "1.15.50"
}

group = property("modGroup")!!
version = property("modVersion")!!

base { archivesName.set(property("modId") as String) }

repositories {
    mavenCentral()
    maven("https://repo.essential.gg/repository/maven-public")
    maven("https://repo.spongepowered.org/maven")
    maven("https://maven.minecraftforge.net")
}

// Libraries packed into the mod jar itself, since 1.8.9 Forge has no dependency loading. Mixin is
// the only one: FML reads the TweakClass manifest attribute and starts it from inside this jar.
val embed: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(embed)

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    // 0.7.11 is the last Mixin that runs on LaunchWrapper; the 0.8 annotation processor still
    // generates a refmap it can read.
    embed("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    // The processor's snapshot POM declares none of what it needs at compile time.
    annotationProcessor("com.google.code.gson:gson:2.10.1")
    annotationProcessor("com.google.guava:guava:32.1.2-jre")
    annotationProcessor("org.ow2.asm:asm-tree:9.6")
    annotationProcessor("org.ow2.asm:asm-commons:9.6")
    annotationProcessor("org.ow2.asm:asm-util:9.6")

    // The Minecraft-free packages (setting, config, threat, detect, hypixel) are tested directly
    // on the build JDK. Anything touching Minecraft has to be checked in-game instead.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    forge {
        // Loom will not unpack the FG2-era Forge artifacts without an explicit Pack200
        // implementation, since the JDK removed its own in Java 14. The shim on the settings
        // buildscript classpath has a matching unpack(InputStream, JarOutputStream) signature,
        // so it drops straight into Loom's functional interface.
        pack200Provider.set(Pack200Provider { input, output ->
            Pack200.newUnpacker().unpack(input, output)
        })
        mixinConfig("mixins.vantage.json")
    }
    mixin {
        // The legacy processor is the one that writes a refmap translating the dev names used in
        // the mixins to the SRG names the game runs with. Without it every hook misses in game.
        useLegacyMixinAp.set(true)
        defaultRefmapName.set("mixins.vantage.refmap.json")
    }
    runs {
        named("client") {
            // In production FML finds the tweaker through the jar manifest; the dev launch has no
            // jar, so it has to be named here.
            programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
            property("mixin.debug.countInjections", "true")
        }
    }
}

// LaunchWrapper casts the system class loader to URLClassLoader, which stopped being true after
// Java 8, so the game itself has to run on 8 even though the build does not.
tasks.named<JavaExec>("runClient") {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
}

tasks.jar {
    from(embed.map { zipTree(it) }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "LICENSE.txt")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes(
        // Mixin has to start before Minecraft's classes load, which only a tweaker can do. These
        // four attributes make FML treat the jar as both a tweaker and an ordinary mod.
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true",
        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
        "MixinConfigs" to "mixins.vantage.json",
    )
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Minecraft 1.8.9 runs on Java 8. Compiling with --release keeps us honest about the API
    // surface even though the build itself runs on a modern JDK.
    options.release.set(8)
    // Targeting 8 is the point, so the "source value 8 is obsolete" notice is just noise.
    options.compilerArgs.add("-Xlint:-options")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") {
        expand(
            "version" to project.version,
            "modId" to project.property("modId"),
            "modName" to project.property("modName"),
        )
    }
}
