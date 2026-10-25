plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "dev.lootbuyer"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    // Compiled against the oldest supported API, so nothing newer than 1.16.5 can sneak in.
    // Newer servers keep these methods, so the same jar runs on 1.16.5 through 26.x.
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.11.6") { isTransitive = false }

    // Paper has no MiniMessage before 1.18.2, and its Adventure version differs per release,
    // so the plugin bundles its own copy under a relocated package.
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.17.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Java 8 bytecode: many 1.16.5 servers still run on Java 8 or 11
    options.release.set(8)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveFileName.set("LootBuyer-${project.version}.jar")
    relocate("net.kyori", "dev.lootbuyer.lib.kyori")
    exclude("org/jetbrains/annotations/**", "org/intellij/lang/annotations/**")
    exclude("META-INF/maven/**", "META-INF/versions/**")
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
