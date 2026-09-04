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

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
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
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Minecraft 1.8.9 runs on Java 8. Compiling with --release keeps us honest about the API
    // surface even though the build itself runs on a modern JDK.
    options.release.set(8)
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
