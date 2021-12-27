import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    idea
    kotlin("jvm") version "1.5.31" // "1.6.10"
    id("com.google.protobuf") version "0.8.18"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("org.jetbrains.compose") version "1.0.0" // ""1.0.1"  // "1.1.0-alpha1-dev545" // "1.0.0"
}

group = "com.github.pambrose"
version = "1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenLocal()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

val coroutinesVersion: String by project
val gengrpcVersion: String by project
val grpcVersion: String by project
val logbackVersion: String by project
val loggingVersion: String by project
val protocVersion: String by project
val slf4jVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    implementation(compose.desktop.currentOs)

    implementation("com.github.pambrose:k5-compose:8f36e55")

    implementation("io.grpc:grpc-netty:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")

    implementation("io.grpc:grpc-kotlin-stub:$gengrpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutinesVersion")

    implementation("io.github.microutils:kotlin-logging:$loggingVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.slf4j:jul-to-slf4j:$slf4jVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs =
        listOf(
            "-Xopt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-Xopt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
}

compose.desktop {
    application {
        mainClass = "SharedCanvas"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = ""
            packageVersion = "1.0.0"
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }
    }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protocVersion" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" }
        id("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:$gengrpcVersion:jdk7@jar" }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}

tasks {
    withType<Copy> {
        filesMatching("**/*.proto") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
}

idea {
    module {
        sourceDirs.add(file("${projectDir}/build/generated/source/proto/main/java"))
        sourceDirs.add(file("${projectDir}/build/generated/source/proto/main/grpckt"))
        sourceDirs.add(file("${projectDir}/build/generated/source/proto/main/grpc"))
    }
}