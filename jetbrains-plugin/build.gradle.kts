import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.4"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    intellijPlatform {
        pycharm("2025.3")
        bundledPlugin("org.jetbrains.plugins.terminal")
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.catclapping.jetbrains"
        name = "Cat Clapping"
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild")
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.PyCharmProfessional, "2025.3")
        }
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN
        )
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

val runCommandMatcherTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs CommandMatcherTest assertions."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.catclapping.jetbrains.CommandMatcherTest")
    enableAssertions = true
}

tasks.named("check") {
    dependsOn(runCommandMatcherTest)
}
