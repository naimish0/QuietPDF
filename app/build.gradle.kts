plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class GeneratePrivacyPolicyAsset : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val sourceFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val output = outputDirectory.file("index.html").get().asFile
        output.parentFile.mkdirs()
        sourceFile.get().asFile.copyTo(output, overwrite = true)
    }
}

val generatePrivacyPolicyAsset by tasks.registering(GeneratePrivacyPolicyAsset::class) {
    sourceFile.set(rootProject.layout.projectDirectory.file("docs/index.html"))
    outputDirectory.set(layout.buildDirectory.dir("generated/privacyPolicyAssets"))
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
            buildConfigField(
                "String",
                "ADMOB_NATIVE_ID",
                "\"ca-app-pub-3940256099942544/2247696110\"",
            )
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_ID",
                "\"ca-app-pub-3940256099942544/1033173712\"",
            )
            buildConfigField(
                "String",
                "ADMOB_APP_OPEN_ID",
                "\"ca-app-pub-3940256099942544/9257395921\"",
            )
        }
        release {
            val applicationId = providers.gradleProperty("ADMOB_APP_ID").orNull
            val homeBannerId = providers.gradleProperty("ADMOB_HOME_BANNER_ID").orNull
            val nativeId = providers.gradleProperty("ADMOB_NATIVE_ID").orNull
            val interstitialId = providers.gradleProperty("ADMOB_INTERSTITIAL_ID").orNull
            val appOpenId = providers.gradleProperty("ADMOB_APP_OPEN_ID").orNull
            val applicationConfigured = applicationId?.matches(
                Regex("^ca-app-pub-\\d{16}~\\d{10}$"),
            ) == true
            val validAdUnit = Regex("^ca-app-pub-\\d{16}/\\d{10}$")
            // Keep unconfigured local release builds crash-safe without inventing a production ID.
            // Each placement stays disabled until its own production ID is supplied.
            manifestPlaceholders["admobApplicationId"] = applicationId.takeIf {
                applicationConfigured
            }
                ?: "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("boolean", "ADMOB_ENABLED", applicationConfigured.toString())
            buildConfigField(
                "String",
                "ADMOB_HOME_BANNER_ID",
                "\"${homeBannerId?.takeIf { applicationConfigured && validAdUnit.matches(it) }.orEmpty()}\"",
            )
            buildConfigField(
                "String",
                "ADMOB_NATIVE_ID",
                "\"${nativeId?.takeIf { applicationConfigured && validAdUnit.matches(it) }.orEmpty()}\"",
            )
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_ID",
                "\"${interstitialId?.takeIf { applicationConfigured && validAdUnit.matches(it) }.orEmpty()}\"",
            )
            buildConfigField(
                "String",
                "ADMOB_APP_OPEN_ID",
                "\"${appOpenId?.takeIf { applicationConfigured && validAdUnit.matches(it) }.orEmpty()}\"",
            )
            isMinifyEnabled = true
            isShrinkResources = true
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

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generatePrivacyPolicyAsset,
            GeneratePrivacyPolicyAsset::outputDirectory,
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
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
    // AdMob declares an old WorkManager runtime transitively. Pin the current stable
    // release so startup and its generated Room database remain R8-safe.
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
