plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "2.1.1"
}

configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok:1.18.16")

  runtimeOnly("com.h2database:h2:1.4.200")
  runtimeOnly("org.flywaydb:flyway-core:7.3.1")
  runtimeOnly("org.postgresql:postgresql:42.2.18")

  compileOnly("org.projectlombok:lombok:1.18.16")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("org.springframework.cloud:spring-cloud-starter-aws-messaging:2.2.5.RELEASE")

  implementation("net.javacrumbs.shedlock:shedlock-spring:4.19.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.19.0")

  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")

  implementation("io.jsonwebtoken:jjwt:0.9.1")

  implementation("net.sf.ehcache:ehcache:2.10.6")
  implementation("org.apache.commons:commons-text:1.9")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.0")
  implementation("com.pauldijou:jwt-core_2.11:4.3.0")
  implementation("com.google.code.gson:gson:2.8.6")
  implementation("com.google.guava:guava:30.0-jre")

  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.16")
  testCompileOnly("org.projectlombok:lombok:1.18.16")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.22.0")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("junit:junit:4.13.1")
}
