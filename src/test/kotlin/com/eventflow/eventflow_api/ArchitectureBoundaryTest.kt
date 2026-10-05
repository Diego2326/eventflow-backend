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
            "import com.eventflow.eventflow_api.infrastructure.",
            "import org.springframework.web.",
            "import org.springframework.http.",
            "import org.springframework.data.",
            "import jakarta.persistence.",
            "import tools.jackson."
        )
        files(source.resolve("application")).forEach { file ->
            val text = Files.readString(file)
            forbidden.forEach { dependency ->
                assertFalse(text.contains(dependency), "${file.fileName} depends on $dependency")
            }
        }
    }

    @Test fun `http controllers and spring repositories live in infrastructure`() {
        files(source).forEach { file ->
            val text = Files.readString(file)
            if ("@RestController" in text || "JpaRepository" in text) {
                assertTrue(file.startsWith(source.resolve("infrastructure")),
                    "${file.fileName} belongs in infrastructure")
            }
        }
    }

    private fun files(root: Path): List<Path> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
    }
}
