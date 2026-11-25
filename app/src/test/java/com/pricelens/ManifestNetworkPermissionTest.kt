package com.pricelens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestNetworkPermissionTest {
    @Test
    fun networkPermissionsAreMarkedForRemovalInSourceManifest() {
        val declared = readPermissions(sourceManifest()).sortedBy { it.first }

        assertEquals(
            listOf(
                "android.permission.ACCESS_NETWORK_STATE" to "remove",
                "android.permission.CAMERA" to null,
                "android.permission.INTERNET" to "remove",
            ),
            declared,
        )
    }

    @Test
    fun packagedManifestsContainNoNetworkPermissions() {
        listOf(
            "build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml",
            "build/intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml",
        ).map { File(projectDir(), it) }.forEach { manifest ->
            val declared = readPermissions(manifest)
            assertFalse(
                declared.any { (name, _) -> name in NETWORK_PERMISSIONS },
                "$manifest must not contain network permissions: $declared",
            )
        }
    }

    private fun projectDir(): String =
        System.getProperty("app.projectDir") ?: System.getProperty("user.dir")

    private fun sourceManifest(): File = File(projectDir(), "src/main/AndroidManifest.xml")

    private fun readPermissions(file: File): List<Pair<String, String?>> {
        assertTrue(file.exists(), "Manifest not found: ${file.absolutePath}")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("uses-permission")
        return (0 until nodes.length).map { index ->
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("android:name")?.nodeValue ?: "<missing-name>"
            name to node.attributes.getNamedItem("tools:node")?.nodeValue
        }
    }

    private companion object {
        val NETWORK_PERMISSIONS = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
        )
    }
}
