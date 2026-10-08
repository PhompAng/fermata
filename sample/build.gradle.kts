plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
}

android {
	namespace = "io.github.phompang.fermata.sample"
	compileSdk = 37

	defaultConfig {
		applicationId = "io.github.phompang.fermata.sample"
		minSdk = 26
		targetSdk = 37
		versionCode = 1
		versionName = "0.1.0"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}

	buildFeatures {
		compose = true
		viewBinding = true
	}
}

dependencies {
	implementation(project(":fermata-core"))
	implementation(project(":fermata-compose"))
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.material3)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.activity.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.fragment.ktx)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.core.ktx)
	debugImplementation(libs.compose.ui.tooling)
	testImplementation(project(":fermata-test"))
	testImplementation(libs.junit)
	testImplementation(libs.kotlin.test)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.turbine)
}
