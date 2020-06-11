plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "0.4.1"
}

configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

extra["spring-security.version"] = "5.3.2.RELEASE" // Updated since spring-boot-starter-oauth2-resource-server-2.2.5.RELEASE only pulls in 5.2.2.RELEASE (still affected by CVE-2018-1258 though)

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok:1.18.12")

    runtime("com.h2database:h2:1.4.200")
    runtime("org.flywaydb:flyway-core:6.4.4")
    runtime("org.postgresql:postgresql:42.2.14")

    compileOnly("org.projectlombok:lombok:1.18.12")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    implementation("org.springframework.cloud:spring-cloud-starter-aws-messaging:2.2.2.RELEASE")

    implementation("net.javacrumbs.shedlock:shedlock-spring:4.12.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.12.0")

    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("com.sun.xml.bind:jaxb-impl:2.3.3")
    implementation("com.sun.xml.bind:jaxb-core:2.3.0.1")
    implementation("javax.activation:activation:1.1.1")
    implementation("javax.transaction:javax.transaction-api:1.3")

    implementation("io.jsonwebtoken:jjwt:0.9.1")

    implementation("net.sf.ehcache:ehcache:2.10.6")
    implementation("org.apache.commons:commons-text:1.8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.0")
    implementation("com.pauldijou:jwt-core_2.11:4.3.0")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.google.guava:guava:29.0-jre")

    testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.12")
    testCompileOnly("org.projectlombok:lombok:1.18.12")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.java:junit-dataprovider:1.13.1")
    testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.17.0")
    testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
    testImplementation("com.github.tomakehurst:wiremock-standalone:2.26.3")
    testImplementation("junit:junit:4.13")
}
