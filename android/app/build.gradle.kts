plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xiaoshuo.yijianhuanming"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xiaoshuo.yijianhuanming"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].assets.directories.add(
        layout.buildDirectory.dir("generated/assets/webRuntime").get().asFile.absolutePath,
    )

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.material3)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val repositoryRoot = rootProject.projectDir.parentFile
val generatedRuntime = layout.buildDirectory.file(
    "generated/assets/webRuntime/name-replacer.js",
)

val buildAndroidRuntime by tasks.registering(Exec::class) {
    workingDir(repositoryRoot)
    commandLine("npm", "run", "build:android-runtime")
    inputs.dir(repositoryRoot.resolve("src/android-runtime"))
    inputs.file(repositoryRoot.resolve("src/content/textEngine.ts"))
    inputs.file(repositoryRoot.resolve("src/content/domFilter.ts"))
    outputs.file(generatedRuntime)
}

tasks.matching {
    it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets"
}.configureEach {
    dependsOn(buildAndroidRuntime)
}
