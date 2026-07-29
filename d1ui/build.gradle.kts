plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mobile.app.d1ui"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    flavorDimensions += "env"
    productFlavors {
        create("staging") { dimension = "env" }
        create("preprod") { dimension = "env" }
        create("prod") { dimension = "env" }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":d1core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
}
