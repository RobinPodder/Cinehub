import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        // Repo that hosts the CloudStream gradle plugin + shared tools
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // The CloudStream gradle plugin — makes `cloudstream { ... }` blocks work
        // and adds the `make` task that packages your provider into a .cs3 file
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Helper so each module's build.gradle.kts can use `cloudstream { ... }`
fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

// Shared Android/Kotlin config applied to every provider subproject,
// so you don't have to repeat minSdk/compileSdk/etc. in each module.
subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    extensions.configure<BaseExtension> {
        namespace = "recloudstream"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8) // Required by CloudStream's loader
            freeCompilerArgs.addAll(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions"
            )
        }
    }

    dependencies {
        val implementation by configurations
        // The main CloudStream API your provider code imports (MainAPI, ExtractorLink, etc.)
        implementation("com.github.recloudstream:cloudstream:pre-release")
        implementation("org.jsoup:jsoup:1.17.2")
    }
}
