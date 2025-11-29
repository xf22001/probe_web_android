import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xiaofei.probetool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xiaofei.probetool"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // 从环境变量中获取签名信息。这些变量将由 GitHub Actions 工作流设置。
            val storeFileEnv = System.getenv("SIGNING_KEYSTORE_FILE")
            val storePasswordEnv = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("SIGNING_KEY_ALIAS")
            val keyPasswordEnv = System.getenv("SIGNING_KEY_PASSWORD")

            // 检查所有必要的签名环境变量是否都已设置。
            // 如果它们都存在，则表示我们处于 CI/CD 环境，使用通过 GitHub Secrets 提供的真实发布密钥库。
            if (storeFileEnv != null && storePasswordEnv != null && keyAliasEnv != null && keyPasswordEnv != null) {
                storeFile = file(storeFileEnv)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
                println("Applying release signing config from GitHub Secrets (CI/CD).")
            } else {
                // 如果任何一个环境变量缺失（例如在本地开发时），则回退到使用 Android 默认的调试密钥库。
                // 这允许您在本地成功构建 'release' 版本而无需提供发布密钥库。
                println("Local build: Release signing secrets not found. Falling back to debug keystore for 'release' buildType.")
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android" // debug.keystore 的默认密码
                keyAlias = "androiddebugkey" // debug.keystore 的默认别名
                keyPassword = "android" // debug.keystore 的默认别名密码
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // debug 构建类型默认使用 Android SDK 提供的调试密钥库，通常无需额外配置。
            // 它会自动使用 ~/.android/debug.keystore 进行签名。
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(files("libs/main.aar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3) // For UI components
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}