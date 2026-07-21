plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rameshta.quietpdf"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rameshta.quietpdf"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobApplicationId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("boolean", "ADMOB_ENABLED", "true")
            buildConfigField(
                "String",
                "ADMOB_HOME_BANNER_ID",
                "\"ca-app-pub-3940256099942544/9214589741\"",
            )
        }
        release {
            val applicationId = providers.gradleProperty("ADMOB_APP_ID").orNull
            val homeBannerId = providers.gradleProperty("ADMOB_HOME_BANNER_ID").orNull
            val configured = applicationId?.matches(Regex("^ca-app-pub-\\d{16}~\\d{10}$")) == true &&
                homeBannerId?.matches(Regex("^ca-app-pub-\\d{16}/\\d{10}$")) == true
            // Keep unconfigured local release builds crash-safe without inventing a production ID.
            // Ads stay disabled until both production values are supplied by the release environment.
            manifestPlaceholders["admobApplicationId"] = applicationId.takeIf { configured }
                ?: "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("boolean", "ADMOB_ENABLED", configured.toString())
            buildConfigField(
                "String",
                "ADMOB_HOME_BANNER_ID",
                "\"${homeBannerId.takeIf { configured }.orEmpty()}\"",
            )
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.pdf.document.service)
    implementation(libs.pdfium.android)
    implementation(libs.pdfium.android.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.pdfbox.android) {
        // Password-based AES protection uses Android's JCA provider. Exclude PDFBox's
        // certificate-encryption stack so this feature does not ship unused crypto code.
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
