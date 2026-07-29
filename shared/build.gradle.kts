import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    androidLibrary {
        namespace = "com.nomnomsom.armstrongandgetty.shared"
        compileSdk = 37
        minSdk = 26

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }

        // Google Cast SDK bindings. The xcframework is fetched by scripts/fetch_cast_sdk.sh
        // (run automatically from the Xcode build phase); the app target links it.
        val castSlice = if (iosTarget.name == "iosArm64") "ios-arm64" else "ios-arm64_x86_64-simulator"
        iosTarget.compilations.getByName("main").cinterops.create("GoogleCast") {
            defFile(project.file("src/nativeInterop/cinterop/GoogleCast.def"))
            compilerOpts("-F${rootProject.projectDir}/iOS/Frameworks/GoogleCast.xcframework/$castSlice")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.okio)
            implementation(libs.xmlutil.core)
            implementation(libs.multiplatform.settings)

            implementation(libs.ktor.client.core)

            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            api(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            // The platform player actual connects to the app's Media3 session.
            implementation(libs.media3.session)
            implementation(libs.media3.common)
            // Chromecast button in the shared top bar
            implementation(libs.play.services.cast.framework)
            implementation(libs.androidx.mediarouter)
            // Firebase Analytics + Crashlytics (config json lives in androidApp)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    // Room compiler for every target that compiles the database
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
