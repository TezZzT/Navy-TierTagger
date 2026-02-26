plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.14-SNAPSHOT"
    id("io.freefair.lombok") version "9.1.0"
}

version = "${project.property("mod_version")}+mc${project.property("minecraft_version")}"
group = project.property("maven_group") as String

repositories {
    maven {
        url = uri("https://maven.uku3lig.net/releases")
    }
    maven {
        url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    }
    maven {
        url = uri("https://www.cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    modApi("net.uku3lig:ukulib:${project.property("ukulib_version")}")

    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:${project.property("devauth_version")}")
    modImplementation("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
}

base {
    archivesName = project.property("archives_base_name") as String
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}