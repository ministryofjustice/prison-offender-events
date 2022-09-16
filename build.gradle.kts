plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.5.1-beta"
  kotlin("plugin.spring") version "1.7.10"
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
  annotationProcessor("org.projectlombok:lombok:1.18.24")

  runtimeOnly("com.h2database:h2:2.1.214")
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:postgresql:42.5.0")

  compileOnly("org.projectlombok:lombok:1.18.24")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("com.amazonaws:aws-java-sdk-sns")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.9")

  implementation("net.javacrumbs.shedlock:shedlock-spring:4.41.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.41.0")

  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")

  implementation("io.jsonwebtoken:jjwt:0.9.1")

  implementation("net.sf.ehcache:ehcache")
  implementation("org.apache.commons:commons-text:1.9")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.4")
  implementation("com.pauldijou:jwt-core_2.11:5.0.0")
  implementation("com.google.code.gson:gson:2.9.1")
  implementation("com.google.guava:guava:31.1-jre")

  // needed for record serialisation
  implementation("com.fasterxml.jackson.core:jackson-core:2.13.4")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4")
  implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.4")

  implementation("org.springdoc:springdoc-openapi-ui:1.6.11")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.11")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.11")
  implementation("org.springdoc:springdoc-openapi-security:1.6.11")

  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.24")
  testCompileOnly("org.projectlombok:lombok:1.18.24")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.35.0")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("junit:junit:4.13.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.35.0")
  testImplementation("org.awaitility:awaitility:4.2.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.6.4")
  testImplementation("org.mockito:mockito-inline:4.8.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.2")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(18))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "18"
    }
  }
}
