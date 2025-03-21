@file:Suppress("UnstableApiUsage")

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "8.0.0"
  kotlin("plugin.spring") version "2.1.20"
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
  annotationProcessor("org.projectlombok:lombok:1.18.36")
  compileOnly("org.projectlombok:lombok:1.18.36")

  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.4.2")
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.4.2")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.13.1")

  implementation("org.apache.commons:commons-text:1.13.0")
  implementation("com.pauldijou:jwt-core_2.11:5.0.0")
  implementation("com.google.code.gson:gson:2.12.1")
  implementation("com.google.guava:guava:33.4.5-jre")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.4.2")
  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.36")
  testCompileOnly("org.projectlombok:lombok:1.18.36")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("org.wiremock:wiremock-standalone:3.12.1")
  testImplementation("org.awaitility:awaitility:4.3.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.1")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.29")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.47.0")
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    freeCompilerArgs.add("-Xwhen-guards")
  }
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
