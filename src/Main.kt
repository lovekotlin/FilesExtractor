package com.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * Utility to extract and copy all Kotlin source files from a project directory,
 * excluding build, test, and git directories.
 *
 * This utility is particularly useful for:
 * - Migrating code between divergent repositories
 * - Creating clean backups without metadata
 * - Analyzing project structure
 */
object KotlinFileExtractor {

    // Directories to exclude from search
    private val EXCLUDED_DIRS = setOf(
        "build",
        "test",
        ".gradle",
        ".idea",
        "generated",
        ".git"  // Also ignore Git metadata
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
     * Copies all Kotlin files to a derived destination directory based on project name.
     * If the destination directory exists, it will be deleted first.
     *
     * @param sourceDir The source directory containing Kotlin files
     * @return Number of files copied
     */
    fun copyKotlinFiles(sourceDir: String): Int {
        val sourcePath = Paths.get(sourceDir).toAbsolutePath().normalize()

        // Extract project name from the root directory
        val projectName = sourcePath.fileName.toString()
        val destDirName = "$projectName-files"
        val destPath = sourcePath.parent.resolve(destDirName)

        println("Source directory: $sourcePath")
        println("Project name identified as: $projectName")
        println("Destination directory: $destPath")

        // Delete destination directory if it exists
        if (Files.exists(destPath)) {
            println("Destination directory already exists. Deleting...")
            Files.walk(destPath)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.delete(it) }
            println("Deleted existing directory: $destPath")
        }

        // Create destination directory
        Files.createDirectories(destPath)
        println("Created destination directory: $destPath")

        // Extract and copy Kotlin files
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
    if (args.isEmpty()) {
        println("Usage: KotlinFileExtractor <sourceDir>")
        println("Example: KotlinFileExtractor ./myproject")
        return
    }

    val sourceDir = "args[0]"

    try {
        println("Scanning directory: $sourceDir")
        val kotlinFiles = KotlinFileExtractor.extractKotlinFiles(sourceDir)
        println("Found ${kotlinFiles.size} Kotlin files")

        println("Copying files to project-specific directory...")
        val filesCopied = KotlinFileExtractor.copyKotlinFiles(sourceDir)
        println("Successfully copied $filesCopied Kotlin files")

        // Print additional statistics
        val totalLines = kotlinFiles.sumOf { it.readLines().size }
        println("\nTotal lines of Kotlin code: $totalLines")
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        e.printStackTrace()
    }
}