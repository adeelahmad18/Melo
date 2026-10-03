package com.junkfood.seal.util

import java.io.File

data class StorageSummary(val downloadedBytes: Long, val temporaryBytes: Long) {
    val totalBytes: Long get() = downloadedBytes + temporaryBytes
}

object StorageAnalyzer {
    fun analyze(root: File): StorageSummary {
        val files = root.walkTopDown().filter(File::isFile).toList()
        val temporary = files.filter { it.extension.equals("part", true) || it.extension.equals("tmp", true) }.sumOf(File::length)
        return StorageSummary(downloadedBytes = files.sumOf(File::length) - temporary, temporaryBytes = temporary)
    }

    /** Deletes only incomplete temporary artifacts beneath the selected download directory. */
    fun clearTemporaryFiles(root: File): Int = root.walkTopDown().filter(File::isFile)
        .filter { it.extension.equals("part", true) || it.extension.equals("tmp", true) }
        .count { it.delete() }
}
