import java.util.Locale.getDefault

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    `maven-publish`
}

group = "org.lolicode"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = project.name.lowercase(getDefault())
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/lolicode-org/NCM-API-Lite-Kt")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            name = "Codeberg"
            url = uri("https://codeberg.org/api/packages/lolicode/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Authorization"
                value = System.getenv("CODEBERG_TOKEN")?.let { "token $it" }.orEmpty()
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}
