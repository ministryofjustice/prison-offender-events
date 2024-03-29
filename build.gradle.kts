@file:Suppress("UnstableApiUsage")

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.15.5"
  kotlin("plugin.spring") version "1.9.23"
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
  annotationProcessor("org.projectlombok:lombok:1.18.32")
  compileOnly("org.projectlombok:lombok:1.18.32")

  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:0.2.2")
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:3.1.1")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.33.1")

  implementation("org.apache.commons:commons-text:1.11.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
  implementation("com.pauldijou:jwt-core_2.11:5.0.0")
  implementation("com.google.code.gson:gson:2.10.1")
  implementation("com.google.guava:guava:33.1.0-jre")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0")

  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.32")
  testCompileOnly("org.projectlombok:lombok:1.18.32")

  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.5")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.5")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("org.wiremock:wiremock-standalone:3.4.2")
  testImplementation("junit:junit:4.13.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.2.7")
  testImplementation("org.awaitility:awaitility:4.2.1")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.8.0")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.21") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.21")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.35.0")
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
