import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

fun prop(name: String, fallback: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: fallback

// Backend API (Node on Vercel → Supabase). Override with LG_API_BASE_URL in gradle.properties if your URL differs.
val SAND_API = "https://sand-api.vercel.app/api/v1/"

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.legalguide.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eid.sand"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Deep-link scheme the backend redirects to after Fawry checkout (legalguide://payment/return?orderId=...)
        buildConfigField("String", "PAYMENT_RETURN_SCHEME", "\"legalguide\"")
        // Optional TLS pins for the API host, comma separated "sha256/..." values
        buildConfigField("String", "CERT_PINS", "\"${prop("LG_CERT_PINS", "")}\"")
        // Max upload size in MB (server enforces its own limit too)
        buildConfigField("int", "MAX_UPLOAD_MB", "25")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            // Default: the live API. Backend on your PC + emulator: LG_API_BASE_URL_DEBUG=http://10.0.2.2:4000/api/v1/
            buildConfigField("String", "API_BASE_URL", "\"${prop("LG_API_BASE_URL_DEBUG", SAND_API)}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"${prop("LG_API_BASE_URL", SAND_API)}\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefresh)
    implementation(libs.viewpager2)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.process)
    implementation(libs.activity)
    implementation(libs.fragment)
    implementation(libs.core)
    implementation(libs.splashscreen)
    implementation(libs.browser)
    implementation(libs.work)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    implementation(libs.markwon.core)
    implementation(libs.markwon.tables)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
