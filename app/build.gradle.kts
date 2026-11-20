import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
}

/**
 * The release key lives outside the repository. Its path and password are read from local.properties (ignored by Git):
 * watchdog.release.storeFile, watchdog.release.password, watchdog.release.keyAlias. Without them (for example in a fresh
 * clone) the release package is signed with the local debug key, so the project still builds, but such a package cannot
 * update an installed official release.
 */
val releaseKey: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val hasReleaseKey = listOf("storeFile", "password", "keyAlias")
    .all { !releaseKey.getProperty("watchdog.release.$it").isNullOrBlank() }

android {
    namespace = "com.cgrshn.watchdog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cgrshn.watchdog"
        minSdk = 31
        targetSdk = 37
        versionCode = 5
        versionName = "0.1.2"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKey.getProperty("watchdog.release.storeFile"))
                storePassword = releaseKey.getProperty("watchdog.release.password")
                keyAlias = releaseKey.getProperty("watchdog.release.keyAlias")
                keyPassword = releaseKey.getProperty("watchdog.release.password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the release key when local.properties names it, otherwise with the local debug key (see above).
            // The package itself is never debuggable.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The language is chosen inside the app (English, Turkish). The bundle is not split by language, so the texts of both
    // languages are always installed.
    bundle {
        language {
            enableSplit = false
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/*.kotlin_module", "/META-INF/*.version", "/kotlin/**", "DebugProbesKt.bin")
        }
    }
}

dependencies {
    testImplementation(libs.junit)
}

/**
 * The app's hard rule is checked at build time: The merged manifest holds only the two allowed permissions, no
 * settings-writing, accessibility, device admin or similar privilege appears in any form, exported components are on a
 * known list and every exported service is guarded by a system permission. If the rule breaks, no package is built.
 */
abstract class ManifestCheck : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val variant: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(manifest.get().asFile)
        // Elements are looked up by local name in every namespace: Android's package parser ignores the prefix
        // ("x:uses-permission").
        fun elements(tag: String): List<Element> {
            val list = document.getElementsByTagNameNS("*", tag)
            return (0 until list.length).map { list.item(it) as Element }
        }
        fun Element.android(attribute: String): String = getAttributeNS(androidNs, attribute)

        val errors = mutableListOf<String>()

        val allElements = document.getElementsByTagNameNS("*", "*")
        for (index in 0 until allElements.length) {
            val element = allElements.item(index) as Element
            if (!element.namespaceURI.isNullOrEmpty()) errors += "No namespaced elements allowed: ${element.tagName}"
        }
        val root = document.documentElement
        if (root.android("sharedUserId").isNotEmpty() || root.android("sharedUserLabel").isNotEmpty()) {
            errors += "No shared user ID (sharedUserId): Another app's permissions would come in this way."
        }

        val expectedPermissions = sortedSetOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECEIVE_BOOT_COMPLETED",
        )
        val permissions = (elements("uses-permission") + elements("uses-permission-sdk-23"))
            .map { it.android("name") }.toSortedSet()
        if (permissions != expectedPermissions) {
            errors += "Permissions differ from the expected set. Found: $permissions. Expected: $expectedPermissions."
        }

        // None of these privileges may guard any component.
        val forbiddenPrivileges = setOf(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            "android.permission.BIND_VPN_SERVICE",
            "android.permission.BIND_INPUT_METHOD",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.WRITE_SETTINGS",
            "android.permission.INTERNET",
        )
        val components = listOf("activity", "activity-alias", "receiver", "service", "provider")
            .flatMap { kind -> elements(kind).map { kind to it } }
        for ((kind, element) in components) {
            val guard = element.android("permission")
            if (guard in forbiddenPrivileges) errors += "$kind ${element.android("name")} is guarded by a forbidden privilege: $guard"
        }
        if (elements("provider").isNotEmpty()) errors += "No content provider allowed."
        if (elements("permission").isNotEmpty()) errors += "The app must not define its own permission."

        // Exported components and the system permissions that guard them.
        val exported = mapOf(
            "com.cgrshn.watchdog.MainActivity" to "",
            "com.cgrshn.watchdog.BootReceiver" to "",
            "com.cgrshn.watchdog.SettingChangeJob" to "android.permission.BIND_JOB_SERVICE",
        )
        for ((kind, element) in components) {
            val name = element.android("name")
            val isExported = element.android("exported")
            if (isExported.isEmpty()) errors += "$kind $name must state android:exported explicitly."
            if (isExported == "true") {
                val expectedGuard = exported[name]
                if (expectedGuard == null) errors += "$kind $name must not be exported."
                else if (element.android("permission") != expectedGuard) errors += "$kind $name must be guarded by '$expectedGuard'."
            }
        }

        val application = elements("application").single()
        if (application.android("allowBackup") != "false") errors += "Backup must be off (allowBackup=false)."
        if (variant.get() == "release" && application.android("debuggable") == "true") errors += "The release package must not be debuggable."

        if (errors.isNotEmpty()) {
            throw GradleException("Manifest check failed:\n" + errors.joinToString("\n") { "- $it" })
        }
        report.get().asFile.writeText("Passed. Permissions: $permissions\n")
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<ManifestCheck>("manifestCheck$name") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            this.variant.set(variant.name)
            report.set(layout.buildDirectory.file("manifest-check/${variant.name}.txt"))
        }
        tasks.configureEach {
            if (this.name == "package$name" || this.name == "package${name}Bundle" || this.name == "check") dependsOn(task)
        }
    }
}
