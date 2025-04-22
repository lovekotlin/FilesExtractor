// Path: src/main/kotlin/com/utils/KotlinFileExtractor.kt

package com.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.toList

/**
 * Utility to extract and copy all Kotlin source files from a project directory,
 * excluding build and test directories.
 *
 * This utility is particularly useful for:
 * - Analyzing project structure
 * - Generating documentation
 * - Code auditing
 * - Migration tasks
 * - Creating filtered backups of source code
 */
object KotlinFileExtractor {

    // Directories to exclude from search
    private val EXCLUDED_DIRS = setOf(
        "build",
        "test",
        ".gradle",
        ".idea",
        "generated"
    )

    /**
     * Extracts all Kotlin files from the specified directory and its subdirectories.
     *
     * @param rootDir The root directory to start searching from
     * @return List of Kotlin source files
     */
    fun extractKotlinFiles(rootDir: String): List<File> {
        val rootPath = Paths.get(rootDir)

        if (!Files.exists(rootPath)) {
            throw IllegalArgumentException("Directory does not exist: $rootDir")
        }

        if (!Files.isDirectory(rootPath)) {
            throw IllegalArgumentException("Path is not a directory: $rootDir")
        }

        return Files.walk(rootPath)
            .filter { shouldIncludePath(it) }
            .filter { it.toString().endsWith(".kt") }
            .map { it.toFile() }
            .toList()
    }

    /**
     * Copies all Kotlin files to the destination directory, preserving the relative path structure.
     *
     * @param sourceDir The source directory containing Kotlin files
     * @param destDir The destination directory to copy files to
     * @return Number of files copied
     */
    fun copyKotlinFiles(sourceDir: String, destDir: String): Int {
        val sourcePath = Paths.get(sourceDir).toAbsolutePath().normalize()
        val destPath = Paths.get(destDir).toAbsolutePath().normalize()

        // Create destination directory if it doesn't exist
        Files.createDirectories(destPath)

        val kotlinFiles = extractKotlinFiles(sourceDir)
        var filesCopied = 0

        kotlinFiles.forEach { sourceFile ->
            try {
                val relativePath = Paths.get(sourceFile.absolutePath).relativeTo(sourcePath)
                val targetPath = destPath.resolve(relativePath)

                // Create parent directories if they don't exist
                Files.createDirectories(targetPath.parent)

                // Copy the file
                Files.copy(
                    sourceFile.toPath(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
                )

                filesCopied++
            } catch (e: Exception) {
                System.err.println("Failed to copy ${sourceFile.absolutePath}: ${e.message}")
            }
        }

        return filesCopied
    }

    /**
     * Determines if a path should be included in the search.
     * Excludes directories specified in EXCLUDED_DIRS.
     *
     * @param path The path to check
     * @return true if the path should be included, false otherwise
     */
    private fun shouldIncludePath(path: Path): Boolean {
        // Get all directory names in the path
        val pathElements = generateSequence(path) { it.parent }
            .map { it.name }
            .toList()

        // Check if any directory in the path is in the excluded list
        return pathElements.none { it in EXCLUDED_DIRS }
    }

    /**
     * Prints information about Kotlin files found in the project.
     *
     * @param files List of Kotlin files
     */
    fun printFileInfo(files: List<File>) {
        println("Found ${files.size} Kotlin files:")

        files.forEach { file ->
            println("- ${file.absolutePath}")
        }

        // Group files by package
        val packageGroups = files.groupBy { file ->
            extractPackage(file)
        }

        println("\nFiles by package:")
        packageGroups.forEach { (pkg, pkgFiles) ->
            println("- $pkg: ${pkgFiles.size} files")
        }
    }

    /**
     * Extracts the package name from a Kotlin file.
     *
     * @param file The Kotlin file
     * @return The package name or "No package" if not found
     */
    private fun extractPackage(file: File): String {
        val packageRegex = """package\s+([a-zA-Z0-9_.]+)""".toRegex()
        val content = file.readText()
        val matchResult = packageRegex.find(content)

        return matchResult?.groupValues?.getOrNull(1) ?: "No package"
    }
}

/**
 * Main function to demonstrate usage of the KotlinFileExtractor.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: KotlinFileExtractor <sourceDir> <destDir>")
        println("Example: KotlinFileExtractor ./myproject ./kotlin-files-backup")
        return
    }

    val sourceDir = args[0]
    val destDir = args[1]

    try {
        println("Scanning directory: $sourceDir")
        val kotlinFiles = KotlinFileExtractor.extractKotlinFiles(sourceDir)
        println("Found ${kotlinFiles.size} Kotlin files")

        println("Copying files to: $destDir")
        val filesCopied = KotlinFileExtractor.copyKotlinFiles(sourceDir, destDir)
        println("Successfully copied $filesCopied Kotlin files")

        // Print additional statistics
        val totalLines = kotlinFiles.sumOf { it.readLines().size }
        println("\nTotal lines of Kotlin code: $totalLines")
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
    }
}