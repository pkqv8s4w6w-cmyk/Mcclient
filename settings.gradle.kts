pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://maven.architectury.dev")
        maven("https://maven.fabricmc.net")
        maven("https://maven.minecraftforge.net")
    }
}

// JDK 14 removed Pack200, and 1.8.9 Forge artifacts are still pack200-compressed. Loom looks up a
// provider via ServiceLoader on its own classloader, so this has to sit on the settings-level
// buildscript classpath - applying it as a project plugin puts it in a child loader Loom can't see.
buildscript {
    repositories {
        maven("https://maven.architectury.dev")
        mavenCentral()
    }
    dependencies {
        classpath("dev.architectury:architectury-pack200:0.1.3")
    }
}

rootProject.name = "Vantage"
