import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Gemini API key for the Phonics "Buddy" — read from local.properties (which is
// git-ignored) so the secret never lands in source control. Leave it blank to
// disable the AI features; the phonics mini-games still work fully offline.
val localProps: Properties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val geminiApiKey: String = localProps.getProperty("GEMINI_API_KEY", "")
// Cloudflare Workers AI — the free-tier image fallback for the AI Art Studio
// (Flux) when the Gemini image model has no quota. Both optional; leave blank to
// disable the fallback. Never hard-coded — read from git-ignored local.properties.
val cloudflareAccountId: String = localProps.getProperty("CLOUDFLARE_ACCOUNT_ID", "")
val cloudflareAiToken: String = localProps.getProperty("CLOUDFLARE_AI_TOKEN", "")

// LibGDX game engine (Escape Room). Declared before `dependencies {}` uses them.
val gdxVersion = "1.13.1" // 1.13.x natives are aligned for 16 KB memory pages
val gdxNatives: Configuration by configurations.creating

android {
    namespace = "sg.com.tertiarycourses.ai4kids"
    compileSdk = 34

    defaultConfig {
        applicationId = "sg.com.tertiarycourses.ai4kids"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "CLOUDFLARE_ACCOUNT_ID", "\"$cloudflareAccountId\"")
        buildConfigField("String", "CLOUDFLARE_AI_TOKEN", "\"$cloudflareAiToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Networking for the online "Brain Arcade" card games (talks to the
    // ai4kids Next.js backend: NextAuth login + /api/learn/cards/* endpoints).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Keystore-backed encrypted prefs for the auth session cookie (CardApi).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // LibGDX — the 2D game engine powering the Escape Room (runs in its own
    // AndroidApplication Activity, launched from the Compose home grid).
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    // Native .so libraries, extracted into jniLibs by copyGdxNatives (below).
    gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// --- LibGDX native libraries -------------------------------------------------
// The gdx-platform "natives-*" jars hold the per-ABI .so files at their root;
// extract them into src/main/jniLibs/<abi> so AGP packages them into the APK.
tasks.register("copyGdxNatives") {
    val outDir = layout.projectDirectory.dir("src/main/jniLibs")
    outputs.dir(outDir)
    doLast {
        gdxNatives.resolve().forEach { jar ->
            val abi = jar.name.substringAfter("natives-").substringBeforeLast(".jar")
            val dest = outDir.dir(abi).asFile
            dest.mkdirs()
            copy {
                from(zipTree(jar)) { include("*.so") }
                into(dest)
            }
        }
    }
}
tasks.named("preBuild") { dependsOn("copyGdxNatives") }
