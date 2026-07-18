pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    // devcode940: removed google repository and com.google group filters
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    // devcode940: removed google() repository
  }
}

rootProject.name = "NexusMedia"

include(":app")
