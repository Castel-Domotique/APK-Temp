plugins { id("com.android.application") }
android {
    namespace = "fr.networkguard.audit"
    compileSdk = 35
    defaultConfig {
        applicationId = "fr.networkguard.audit"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.7.2"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.9.4")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.9.4")
    implementation("androidx.media3:media3-ui:1.9.4")
    testImplementation("junit:junit:4.13.2")
}
