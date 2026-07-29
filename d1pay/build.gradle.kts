plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.mobile.app.d1pay"
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
}

dependencies {
    implementation(project(":d1core"))

    testImplementation(libs.junit)
}
