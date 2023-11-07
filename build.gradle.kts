@file:Suppress("UnstableApiUsage")

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.8.0"
  kotlin("plugin.spring") version "1.9.20"
}

configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

testing {
  suites {
    register<JvmTestSuite>("testSmoke") {
      dependencies {
        implementation(project())
      }
    }
  }
}
configurations["testSmokeImplementation"].extendsFrom(configurations["testImplementation"])

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok:1.18.30")
  compileOnly("org.projectlombok:lombok:1.18.30")

  implementation("org.springframework.boot:spring-boot-starter-actuator")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-cache")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.1.1")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.31.0")

  implementation("org.apache.commons:commons-text:1.11.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")
  implementation("com.pauldijou:jwt-core_2.11:5.0.0")
  implementation("com.google.code.gson:gson:2.10.1")
  implementation("com.google.guava:guava:32.1.3-jre")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
  testCompileOnly("org.projectlombok:lombok:1.18.30")

  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.3")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.3")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("org.wiremock:wiremock:3.2.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.2.2")
  testImplementation("org.awaitility:awaitility:4.2.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.7.3")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.18") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.18")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.31.0")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "21"
    }
  }
}
