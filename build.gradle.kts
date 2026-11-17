import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * Der verbindliche Gate: Lint + alle Unit-Tests + Pack-/Asset-Validator +
 * APK-Groessenpruefung (<= 45 MB). Ein Block ist erst fertig, wenn dieser
 * Task gruen ist.
 */
val assetValidator = tasks.register<Exec>("validatePacks") {
    group = "verification"
    description = "Validiert Source-Packs, Hoster, Skins, Katalog und Fixtures"
    workingDir = rootDir
    commandLine("python3", "tools/validate_packs.py", "--root", rootDir.absolutePath)
}

val fetchToolTests = tasks.register<Exec>("testFetchTools") {
    group = "verification"
    description = "Unit-Tests fuer den HTML-/CSS-Matcher des Fetch-Workers"
    workingDir = rootDir
    commandLine(
        "python3", "-m", "unittest", "discover",
        "-s", "tools/fetch", "-p", "test_*.py"
    )
}

val packsReport = tasks.register<Exec>("packsReport") {
    group = "verification"
    description = "Prueft, ob docs/PACKS.md zu den Assets passt"
    workingDir = rootDir
    commandLine("python3", "tools/packs_report.py", "--root", rootDir.absolutePath, "--check")
}

val apkSizeLimitBytes = 45L * 1024L * 1024L

val checkApkSize = tasks.register("checkApkSize") {
    group = "verification"
    description = "Prueft die Debug-APK gegen die 45-MB-Grenze"
    dependsOn(":app:assembleDebug")
    doLast {
        val apk = rootDir.resolve("app/build/outputs/apk/debug/app-debug.apk")
        if (!apk.isFile) {
            throw GradleException("APK fehlt: ${apk.absolutePath}")
        }
        val bytes = apk.length()
        val mib = bytes / (1024.0 * 1024.0)
        if (bytes > apkSizeLimitBytes) {
            throw GradleException(
                "APK zu gross: %.1f MiB (%,d Bytes) > 45 MiB".format(mib, bytes)
            )
        }
        logger.lifecycle("APK-Groesse: %.1f MiB (%,d Bytes) — Grenze 45 MiB".format(mib, bytes))
    }
}

val unitTestModules = listOf(
    ":app",
    ":core:data",
    ":core:design",
    ":core:meta",
    ":core:net",
    ":player",
    ":recs",
    ":scraper:engine",
    ":scraper:resolver",
    ":sources:free"
)

tasks.register("gate") {
    group = "verification"
    description = "Frame61-Gate: Lint, Unit-Tests, Asset-Validator, APK-Groesse"
    dependsOn(assetValidator)
    dependsOn(fetchToolTests)
    dependsOn(packsReport)
    dependsOn(checkApkSize)
    dependsOn(":app:lint")
    dependsOn(unitTestModules.map { "$it:testDebugUnitTest" })
}
