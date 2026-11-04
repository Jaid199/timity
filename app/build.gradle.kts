import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Supabase credentials come from the git-ignored `.env` at the repo root (see docs/SUPABASE.md).
 * Both the plain names and the NEXT_PUBLIC_-prefixed names the Supabase dashboard prints are accepted.
 * A missing file yields empty strings so the app still builds; sync is simply disabled.
 */
val dotEnv: Map<String, String> = rootProject.file(".env").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.reader().use { load(it) } }
        .entries.associate { (k, v) -> k.toString().trim() to v.toString().trim().trim('"', '\'') }
} ?: emptyMap()

fun env(vararg names: String): String = names.firstNotNullOfOrNull { dotEnv[it]?.takeIf(String::isNotBlank) } ?: ""

val supabaseUrl = env("SUPABASE_URL", "NEXT_PUBLIC_SUPABASE_URL")
val supabasePublishableKey = env("SUPABASE_PUBLISHABLE_KEY", "NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY")

android {
    namespace = "com.jaid.timity"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jaid.timity"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
