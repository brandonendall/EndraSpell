pluginManagement { repositories {
 google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.google\\.android.*"); includeGroupByRegex("com\\.google\\.mlkit.*"); includeGroup("com.google.testing.platform"); includeGroupByRegex("com\\.google\\.firebase.*") } }
 mavenCentral(); gradlePluginPortal()
} }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories {
 google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.google\\.android.*"); includeGroupByRegex("com\\.google\\.mlkit.*"); includeGroup("com.google.testing.platform"); includeGroupByRegex("com\\.google\\.firebase.*") } }
 mavenCentral()
} }
rootProject.name = "EndraSpell"
include(":app")
