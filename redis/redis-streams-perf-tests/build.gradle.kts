import lco.gradle.UberJar

plugins {
    scala
    id("lco.gradle.uberJar")
}

group = "lco-redis-perf-tests"
version = rootProject.version

UberJar {
    mainClass.set("lco.redis.perf.tests.MainApp")
}

dependencies {
    implementation(project(":configurations"))

    implementation(libs.scala.s2)
    implementation(libs.zio.s2)
    implementation(libs.zio.logging.s2)
    implementation(libs.zio.logging.slf4j.s2)
    implementation(libs.lettuce.core)
    implementation(libs.reactive.streams)
    implementation(libs.zio.config.s2)
    implementation(libs.zio.config.typesafe.s2)
}