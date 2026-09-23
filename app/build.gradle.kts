plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.modificator.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zyyme.einklauncher"
        minSdk = 14
        targetSdk = 36
        versionCode = 49
        versionName = "3.3"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/license.txt")
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
        // MINA 2.1.6+ calls ServerSocketChannel.supportedOptions(), which is
        // unavailable on old Android releases this launcher still supports.
        force("org.apache.mina:mina-core:2.1.3")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("org.apache.ftpserver:ftplet-api:1.2.1")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.1")
}
