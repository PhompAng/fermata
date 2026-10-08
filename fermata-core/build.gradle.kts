import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.maven.publish)
}

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_21)
	}
}

dependencies {
	api(libs.kotlinx.coroutines.core)
	testImplementation(libs.junit)
	testImplementation(libs.kotlin.test)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.turbine)
}

mavenPublishing {
	publishToMavenCentral()
	signAllPublications()
	configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))
}
