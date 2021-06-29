plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.3.1"
  kotlin("plugin.spring") version "1.5.10"
}

configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok:1.18.20")

  runtimeOnly("com.h2database:h2:1.4.200")
  runtimeOnly("org.flywaydb:flyway-core:7.10.0")
  runtimeOnly("org.postgresql:postgresql:42.2.22")

  compileOnly("org.projectlombok:lombok:1.18.20")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("org.springframework.cloud:spring-cloud-starter-aws-messaging:2.2.6.RELEASE")
  implementation("org.springframework:spring-jms")
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")

  implementation("net.javacrumbs.shedlock:shedlock-spring:4.24.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.24.0")

  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")

  implementation("io.jsonwebtoken:jjwt:0.9.1")

  implementation("net.sf.ehcache:ehcache")
  implementation("org.apache.commons:commons-text:1.9")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.3")
  implementation("com.pauldijou:jwt-core_2.11:5.0.0")
  implementation("com.google.code.gson:gson:2.8.7")
  implementation("com.google.guava:guava:30.1.1-jre")

  // needed for record serialisation
  implementation("com.fasterxml.jackson.core:jackson-core:2.12.3")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.12.3")
  implementation("com.fasterxml.jackson.core:jackson-annotations:2.12.3")

  implementation("org.springdoc:springdoc-openapi-ui:1.5.9")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.5.9")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.9")

  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.20")
  testCompileOnly("org.projectlombok:lombok:1.18.20")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.27.0")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("junit:junit:4.13.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.27.0")
  testImplementation("org.awaitility:awaitility:4.1.0")
}

tasks {
  compileKotlin {
    kotlinOptions {
      jvmTarget = "16"
    }
  }
}
