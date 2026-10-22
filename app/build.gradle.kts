import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Địa chỉ REST API: đặt "vego.apiBaseUrl" trong local.properties (ưu tiên) hoặc gradle.properties.
// Máy ảo Android gọi server chạy trên máy tính qua 10.0.2.2.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val apiBaseUrl: String = (localProps.getProperty("vego.apiBaseUrl")
    ?: providers.gradleProperty("vego.apiBaseUrl").orNull
    ?: "http://10.0.2.2:8080/").let { if (it.endsWith("/")) it else "$it/" }

android {
    namespace = "com.example.vego"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.vego"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    // Tạo mã QR cho vé điện tử
    implementation(libs.zxing.core)
    // Gọi REST API của server VéGo
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}