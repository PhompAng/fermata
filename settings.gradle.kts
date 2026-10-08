pluginManagement {
	repositories {
		gradlePluginPortal()
		google()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "Fermata"
include(":fermata-core")
include(":fermata-compose")
include(":fermata-test")
include(":sample")
