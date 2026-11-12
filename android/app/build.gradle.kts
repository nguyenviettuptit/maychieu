plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Khóa ký: GitHub tự tạo và cất trong bộ nhớ đệm riêng của bản build (không nằm trong mã nguồn công khai).
// Đường dẫn và mật khẩu truyền qua biến môi trường.
val keystorePath = System.getenv("KEYSTORE_PATH") ?: ""
val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""

android {
    namespace = "vn.maychieu.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.maychieu.app"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "2.0." + (System.getenv("VERSION_CODE") ?: "1")
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystorePath)
            storeType = "pkcs12"
            storePassword = keystorePassword
            keyAlias = "maychieu"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
