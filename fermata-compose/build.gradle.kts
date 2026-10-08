import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.maven.publish)
}

android {
	namespace = "io.github.phompang.fermata.compose"
	compileSdk = 37

	defaultConfig {
		minSdk = 23
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}

	buildFeatures {
		compose = true
	}
}

dependencies {
	api(project(":fermata-core"))
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.runtime)
}

mavenPublishing {
	publishToMavenCentral()
	signAllPublications()
	configure(
		AndroidSingleVariantLibrary(
			javadocJar = JavadocJar.Empty(),
			sourcesJar = SourcesJar.Sources(),
			variant = "release",
		),
	)
}
