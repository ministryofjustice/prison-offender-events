@file:Suppress("UnstableApiUsage")

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.4.0"
  kotlin("plugin.spring") version "1.9.10"
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
  annotationProcessor("org.projectlombok:lombok:1.18.28")
  compileOnly("org.projectlombok:lombok:1.18.28")

  implementation("org.springframework.boot:spring-boot-starter-actuator")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-cache")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.0.1")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.29.0")

  implementation("org.apache.commons:commons-text:1.10.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
  implementation("com.pauldijou:jwt-core_2.11:5.0.0")
  implementation("com.google.code.gson:gson:2.10.1")
  implementation("com.google.guava:guava:32.1.2-jre")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.28")
  testCompileOnly("org.projectlombok:lombok:1.18.28")

  testImplementation("io.jsonwebtoken:jjwt-impl:0.11.5")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.11.5")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.0.0")
  testImplementation("org.awaitility:awaitility:4.2.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.7.3")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.16")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.29.0")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(20))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "20"
    }
  }
}
