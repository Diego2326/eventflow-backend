package com.eventflow.eventflow_api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ArchitectureBoundaryTest {
    private val source = Path.of("src/main/kotlin/com/eventflow/eventflow_api")

    @Test fun `application does not import infrastructure or web frameworks`() {
        val forbidden = listOf(
            "import org.springframework.web.",
            "import org.springframework.http.",
            "import org.springframework.data.",
            "import jakarta.persistence.",
            "import tools.jackson."
        )
        files(source).filter { "application" in it.iterator().asSequence().map(Path::toString).toList() }.forEach { file ->
            val text = Files.readString(file)
            assertFalse(Regex("import com\\.eventflow\\.eventflow_api\\.[^\\n]*\\.infrastructure\\.").containsMatchIn(text),
                "${file.fileName} depends on infrastructure")
            forbidden.forEach { dependency ->
                assertFalse(text.contains(dependency), "${file.fileName} depends on $dependency")
            }
        }
    }

    @Test fun `features own their layers`() {
        files(source).filter { it.fileName.toString() != "EventflowApiApplication.kt" }.forEach { file ->
            val parts = source.relativize(file).iterator().asSequence().map(Path::toString).toList()
            assertTrue(parts.size >= 3, "${file.fileName} must live inside a feature and a layer")
            assertTrue(parts[1] in setOf("application", "domain", "infrastructure"),
                "${file.fileName} must live in a layer within its feature")
        }
    }

    @Test fun `http controllers and spring repositories live in infrastructure`() {
        files(source).forEach { file ->
            val text = Files.readString(file)
            if ("@RestController" in text || "JpaRepository" in text) {
                assertTrue("infrastructure" in file.iterator().asSequence().map(Path::toString).toList(),
                    "${file.fileName} belongs in infrastructure")
            }
        }
    }

    private fun files(root: Path): List<Path> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
    }
}
