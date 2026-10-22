plugins {
    `java-library`
    id("net.fabricmc.fabric-loom") version "1.17.21"
}

version = rootProject.property("modVersion") as String
group = rootProject.property("modGroup") as String
base { archivesName.set("theseus-fabric-${property("minecraftVersion")}") }
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}
sourceSets.main {
    java.srcDir(rootProject.file("common/src/main/java"))
    resources.srcDirs(rootProject.file("common/src/main/resources"), rootProject.file("examples/theseus-demo"))
}
repositories {
    mavenCentral()
    maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") {
        content { includeGroup("fuzs.forgeconfigapiport") }
    }
    maven("https://maven.teamresourceful.com/repository/maven-public/")
    maven("https://maven.blamejared.com")
    maven("https://maven.shedaniel.me")
    maven("https://maven.architectury.dev")
    maven("https://chocolateminecraft.com/maven") { content { includeGroup("xaero.pac") } }
}
dependencies {
    minecraft("com.mojang:minecraft:${property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabricApiVersion")}")
    implementation("com.teamresourceful.resourcefullib:resourcefullib-fabric-26.1:${property("resourcefulLibVersion")}")
    compileOnly("com.teamresourceful.resourcefulconfig:resourcefulconfig-fabric-26.1:${property("resourcefulConfigVersion")}")
    runtimeOnly("com.teamresourceful.resourcefulconfig:resourcefulconfig-fabric-26.1:${property("resourcefulConfigVersion")}")
    implementation("earth.terrarium.olympus:olympus-fabric-26.1:${property("olympusVersion")}")
    include("earth.terrarium.olympus:olympus-fabric-26.1:${property("olympusVersion")}")
    compileOnly("xaero.pac:open-parties-and-claims-fabric-26.1.2:${property("openPartiesAndClaimsVersion")}")
    compileOnly("mezz.jei:jei-26.1.2-common-api:${property("jeiVersion")}")
    compileOnly("me.shedaniel.cloth:basic-math:0.6.1")
    compileOnly("me.shedaniel:RoughlyEnoughItems-fabric:${property("reiVersion")}")
    compileOnly("dev.architectury:architectury-fabric:${property("architecturyVersion")}")
    if (providers.gradleProperty("opac").isPresent) {
        runtimeOnly("xaero.pac:open-parties-and-claims-fabric-26.1.2:${property("openPartiesAndClaimsVersion")}")
    }
    when (providers.gradleProperty("recipeViewer").orNull?.lowercase()) {
        "rei" -> {
            runtimeOnly("me.shedaniel:RoughlyEnoughItems-fabric:${property("reiVersion")}")
            runtimeOnly("dev.architectury:architectury-fabric:${property("architecturyVersion")}")
        }
        "jei" -> runtimeOnly("mezz.jei:jei-26.1.2-fabric:${property("jeiVersion")}")
    }
}
tasks.processResources {
    val replacements = mapOf("version" to project.version, "minecraftVersion" to project.property("minecraftVersion"), "fabricLoaderVersion" to project.property("fabricLoaderVersion"), "fabricApiVersion" to project.property("fabricApiVersion"))
    inputs.properties(replacements)
    filesMatching("fabric.mod.json") { expand(replacements) }
    // Retained legacy mixin definitions have no implementation in the 26.1.2 port.
    exclude("theseus-common.mixins.json")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
