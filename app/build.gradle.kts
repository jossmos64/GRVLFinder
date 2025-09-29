plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "be.kuleuven.gt.grvlfinder"
    compileSdk = 36

    defaultConfig {
        applicationId = "be.kuleuven.gt.grvlfinder"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // osmdroid voor OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.16")
    implementation("com.google.android.material:material:1.9.0")
    implementation ("androidx.preference:preference:1.2.1")
    implementation ("androidx.viewpager2:viewpager2:1.0.0")
    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")


}