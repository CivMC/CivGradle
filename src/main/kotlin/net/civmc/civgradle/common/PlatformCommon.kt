package net.civmc.civgradle.common

import com.gradle.enterprise.gradleplugin.GradleEnterpriseExtension
import net.civmc.civgradle.CivGradleExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.language.jvm.tasks.ProcessResources
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

object PlatformCommon {

    private val logger: Logger = LoggerFactory.getLogger(PlatformCommon::class.java)

    private var enterpriseEnabled = false

    fun beforeEvaluate(project: Project, extension: CivGradleExtension) {
        project.gradle.settingsEvaluated {
            if (it.pluginManager.hasPlugin("com.gradle.enterprise")) {
                enterpriseEnabled = true
            }
        }
    }

    fun afterEvaluate(project: Project, extension: CivGradleExtension) {
        if (project.pluginManager.hasPlugin("java-library")) {
            logger.debug("Configuring Java")
            configureJava(project)
        }

        if(project.pluginManager.hasPlugin("maven-publish")) {
            logger.debug("Configuring Maven Publish")
            configureMavenPublish(project, extension)
        }

        if (enterpriseEnabled) {
            logger.debug("Configuring Gradle Enterprise")
            configureGradleEnterprise(project, extension)
        }
    }

    /**
     * Configure our project to use java 17 UTF_8 for everything
     */
    private fun configureJava(project: Project) {
        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)

        javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

        javaExtension.withJavadocJar()
        javaExtension.withSourcesJar()

        project.tasks.withType(JavaCompile::class.java) {
            it.options.encoding = Charsets.UTF_8.name()
            it.options.release.set(17)
        }

        project.tasks.withType(Javadoc::class.java) {
            it.options.encoding = Charsets.UTF_8.name()
        }

        project.tasks.withType(ProcessResources::class.java) {
            it.filteringCharset = Charsets.UTF_8.name()
        }

        project.tasks.withType(Test::class.java) { testing ->
            testing.useJUnitPlatform()
            testing.testLogging { logging ->
                logging.events(*TestLogEvent.values())
                logging.exceptionFormat = TestExceptionFormat.FULL
                logging.showCauses = true
                logging.showExceptions = true
                logging.showStackTraces = true
            }
        }

        logger.debug("Java tasks configured")
    }

    private fun configureMavenPublish(project: Project, extension: CivGradleExtension) {
        val publishingExtension = project.extensions.getByType(PublishingExtension::class.java)

        val githubActor = System.getenv("GITHUB_ACTOR")
        val githubToken = System.getenv("GITHUB_TOKEN")

        val nexusUser = System.getenv("CIVMC_NEXUS_USER")
        val nexusPassword = System.getenv("CIVMC_NEXUS_PASSWORD")

        publishingExtension.repositories {
            if (!githubActor.isNullOrEmpty() && !githubToken.isNullOrEmpty()) {
                it.maven {
                    it.name = "GitHubPackages"
                    it.url = URI("https://maven.pkg.github.com/${extension.repoOwner}/${extension.pluginName}")
                    it.credentials {
                        it.username = githubActor
                        it.password = githubToken
                    }
                }
            }

            if (!nexusUser.isNullOrEmpty() && !nexusPassword.isNullOrEmpty()) {
                val targetRepo = if (project.version.toString().endsWith("SNAPSHOT"))
                    "maven-snapshots"
                else
                    "maven-releases"

                it.maven {
                    it.name = "CivMC"
                    it.url = URI("https://repo.civmc.net/repository/$targetRepo")
                    it.credentials {
                        it.username = nexusUser
                        it.password = nexusPassword
                    }
                }
            }
        }

        publishingExtension.publications {
            it.register("maven", MavenPublication::class.java) {
                it.from(project.components.getByName("java"))
            }
        }
    }

    private fun configureGradleEnterprise(project: Project, extension: CivGradleExtension) {
        val enterpriseExtension = project.extensions.getByType(GradleEnterpriseExtension::class.java)

        enterpriseExtension.buildScan {
            if (!System.getenv("CI").isNullOrEmpty()) {
               it.tag("CI")
                it.termsOfServiceUrl = "https://gradle.com/terms-of-service"
                it.termsOfServiceAgree = "yes"
            }
        }
    }
}
