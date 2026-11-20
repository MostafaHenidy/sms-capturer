plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.medora.paymentsms"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.medora.paymentsms"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-pilot"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Emulator -> host Laravel by default. For a physical-device pilot,
            // override with -PpilotBaseUrl=http://<host-lan-ip>/medora/public/ .
            val pilotBaseUrl = (project.findProperty("pilotBaseUrl") as String?)
                ?: "http://10.0.2.2:8000/"
            buildConfigField("String", "API_BASE_URL", "\"$pilotBaseUrl\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "true")
        }
        create("staging") {
            // Set the real staging host in your local.properties / CI, never a credential.
            buildConfigField("String", "API_BASE_URL", "\"https://staging.example.invalid/\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "false")
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"https://medora.example.invalid/\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Keystore-backed encrypted storage for the revocable device token.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation("androidx.work:work-testing:2.10.1")
}
