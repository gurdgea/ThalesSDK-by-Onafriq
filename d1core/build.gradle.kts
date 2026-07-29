import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

val environments = listOf("staging", "preprod", "prod")

fun loadEnvConfig(env: String): Properties {
    val local = rootProject.file("config/$env.properties")
    val template = rootProject.file("config/$env.properties.example")
    val file = if (local.exists()) local else template
    require(file.exists()) {
        "Missing config/$env.properties. Copy config/$env.properties.example and fill in " +
            "the onboarding values supplied by Thales."
    }
    return Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "com.mobile.app.d1core"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 27
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    flavorDimensions += "env"
    productFlavors {
        environments.forEach { env ->
            create(env) {
                dimension = "env"
                buildConfigField("String", "ENV_NAME", "\"$env\"")
                loadEnvConfig(env).forEach { (rawKey, rawValue) ->
                    val key = rawKey.toString().trim()
                    val value = rawValue.toString().trim()
                    when (key) {
                        "CLIENT_BINDING_ENABLED", "SECURE_LOG_ENABLED" ->
                            buildConfigField("boolean", key, value.ifEmpty { "false" })

                        else ->
                            buildConfigField("String", key, "\"$value\"")
                    }
                }
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    releaseApi(group = "", name = "d1-release-4.4.0", ext = "aar")
    debugApi(group = "", name = "d1-debug-4.4.0", ext = "aar")

    implementation(libs.jna)
    implementation(libs.play.services.base)
    implementation(libs.play.services.basement)
    api(libs.kotlinx.coroutines.android)

    implementation(libs.firebase.messaging)

    // D1Authn (3DS)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.play.services.fido)
    implementation(libs.androidx.biometric)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
