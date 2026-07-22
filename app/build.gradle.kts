import java.io.File

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

abstract class RenameApkArtifact : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFiles
    abstract val inputDirectory: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.Input
    abstract val versionName: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val versionCode: org.gradle.api.provider.Property<Int>

    @get:org.gradle.api.tasks.Input
    abstract val variantName: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Internal
    abstract val transformationRequest: org.gradle.api.provider.Property<
        com.android.build.api.artifact.ArtifactTransformationRequest<RenameApkArtifact>
        >

    @org.gradle.api.tasks.TaskAction
    fun rename() {
        outputDirectory.get().asFile.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.forEach { staleApk ->
                check(staleApk.delete()) { "Could not remove stale APK: ${staleApk.name}" }
            }
        transformationRequest.get().submit(this) { builtArtifact ->
            val input = File(builtArtifact.outputFile)
            val output = outputDirectory.file(
                "QuietPDF-${variantName.get()}-${versionName.get()}-${versionCode.get()}.apk",
            ).get().asFile
            input.copyTo(output, overwrite = true)
            output
        }
    }
}

val generatePrivacyPolicyAsset by tasks.registering(GeneratePrivacyPolicyAsset::class) {
    sourceFile.set(rootProject.layout.projectDirectory.file("docs/index.html"))
    outputDirectory.set(layout.buildDirectory.dir("generated/privacyPolicyAssets"))
}

val quietPdfVersionCode = providers.gradleProperty("VERSION_CODE").orNull
    ?.toIntOrNull()
    ?.takeIf { it in 1..2_100_000_000 }
    ?: throw GradleException(
        "VERSION_CODE must be an integer from 1 through 2100000000 in gradle.properties or -P.",
    )
val quietPdfVersionName = providers.gradleProperty("VERSION_NAME").orNull
    ?.takeIf { Regex("^\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?$").matches(it) }
    ?: throw GradleException(
        "VERSION_NAME must use semantic versioning, for example 1.1.0 or 1.1.0-beta.1.",
    )
val versionedArtifactDirectory = layout.buildDirectory.dir("outputs/versioned")

val exportVersionedReleaseBundle by tasks.registering(org.gradle.api.tasks.Copy::class) {
    from(layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
    into(versionedArtifactDirectory)
    rename { "QuietPDF-release-$quietPdfVersionName-$quietPdfVersionCode.aab" }
}

android {
    namespace = "com.rameshta.quietpdf"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rameshta.quietpdf"
        minSdk = 28
        targetSdk = 36
        versionCode = quietPdfVersionCode
        versionName = quietPdfVersionName

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

    // The in-app language picker can select a locale independently of the device locale.
    // Keep every supported translation installed instead of letting Play deliver only the
    // device-language split from the app bundle.
    bundle {
        language {
            enableSplit = false
        }
    }

}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generatePrivacyPolicyAsset,
            GeneratePrivacyPolicyAsset::outputDirectory,
        )

        val renameApk = tasks.register<RenameApkArtifact>(
            "rename${variant.name.replaceFirstChar(Char::uppercaseChar)}ApkArtifact",
        ) {
            versionName.set(quietPdfVersionName)
            versionCode.set(quietPdfVersionCode)
            variantName.set(variant.name)
            outputDirectory.set(layout.buildDirectory.dir("outputs/apk/${variant.name}"))
        }
        val transformationRequest = variant.artifacts.use(renameApk)
            .wiredWithDirectories(
                RenameApkArtifact::inputDirectory,
                RenameApkArtifact::outputDirectory,
            )
            .toTransformMany(com.android.build.api.artifact.SingleArtifact.APK)
        renameApk.configure {
            this.transformationRequest.set(transformationRequest)
        }
    }
}

// Bundles do not expose APK-style output naming, so keep a clearly named upload copy for AABs.
tasks.configureEach {
    when (name) {
        "bundleRelease" -> finalizedBy(exportVersionedReleaseBundle)
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
