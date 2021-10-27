plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.3.12"
  kotlin("plugin.spring") version "1.5.31"
  id("org.unbroken-dome.test-sets") version "4.0.0"
}

configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

testSets {
  "testSmoke"()
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok:1.18.20")

  runtimeOnly("com.h2database:h2:1.4.200")
  runtimeOnly("org.flywaydb:flyway-core:7.15.0")
  runtimeOnly("org.postgresql:postgresql:42.2.23")

  compileOnly("org.projectlombok:lombok:1.18.20")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("com.amazonaws:aws-java-sdk-sns")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.0.2")

  implementation("net.javacrumbs.shedlock:shedlock-spring:4.26.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.26.0")

  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")

  implementation("io.jsonwebtoken:jjwt:0.9.1")

  implementation("net.sf.ehcache:ehcache")
  implementation("org.apache.commons:commons-text:1.9")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.5")
  implementation("com.pauldijou:jwt-core_2.11:5.0.0")
  implementation("com.google.code.gson:gson:2.8.8")
  implementation("com.google.guava:guava:30.1.1-jre")

  // needed for record serialisation
  implementation("com.fasterxml.jackson.core:jackson-core:2.12.5")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.12.5")
  implementation("com.fasterxml.jackson.core:jackson-annotations:2.12.5")

  implementation("org.springdoc:springdoc-openapi-ui:1.5.10")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.5.10")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.10")

  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.20")
  testCompileOnly("org.projectlombok:lombok:1.18.20")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.28.0")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("junit:junit:4.13.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.28.0")
  testImplementation("org.awaitility:awaitility:4.1.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.1.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.5.2")
  testImplementation("org.mockito:mockito-inline:3.12.4")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(16))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "16"
    }
  }
}
