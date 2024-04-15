plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Persistent caches on disk and cross process locking.
    | Mostly for persisting Maps to the disk.
    | Also contains implementations for in-memory caches in front of the disk cache.
""".trimMargin()

errorprone {
    disabledChecks.addAll(
        "EmptyBlockTag", // 2 occurrences
        "LockNotBeforeTry", // 3 occurrences
        "NonAtomicVolatileUpdate", // 1 occurrences
        "StringCaseLocaleUsage", // 1 occurrences
        "ThreadLocalUsage", // 1 occurrences
        "UnnecessaryLambda", // 1 occurrences
        "UnusedMethod", // 2 occurrences
        "UnusedVariable", // 1 occurrences
        "WaitNotInLoop", // 1 occurrences
    )
}

dependencies {
    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(project(":build-operations"))
    api(project(":base-services"))
    api(project(":messaging"))
    api(project(":native"))
    api(project(":files"))

    api(libs.jsr305)

    implementation(projects.io)
    implementation(projects.time)

    implementation(libs.guava)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)

    testImplementation(project(":core-api"))
    testImplementation(project(":functional"))
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("DefaultPersistentDirectoryCacheTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
