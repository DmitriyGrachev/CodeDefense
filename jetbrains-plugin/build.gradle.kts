import org.gradle.api.tasks.Sync
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.codedefense.jetbrains"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.4")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "dev.codedefense.jetbrains"
        name = "CodeDefense"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "262.*"
        }
        vendor {
            name = "CodeDefense"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

val coreJar = layout.projectDirectory.file("../target/codedefense.jar")
val preparedCli = layout.buildDirectory.dir("prepared-cli")

val syncCodeDefenseCli by tasks.registering(Sync::class) {
    inputs.file(coreJar)
    from(coreJar)
    into(preparedCli)
    rename { "codedefense.jar" }
}

tasks.named<Sync>("prepareSandbox") {
    dependsOn(syncCodeDefenseCli)
    from(preparedCli) {
        into("${project.name}/cli")
    }
}
