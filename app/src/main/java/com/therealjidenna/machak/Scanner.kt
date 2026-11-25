package com.therealjidenna.machak

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import java.io.File
import java.security.MessageDigest

data class DupFile(val path: String, val size: Long, val modified: Long)

data class DupGroup(val size: Long, val files: List<DupFile>)

data class AppItem(val label: String, val pkg: String, val version: String)

object FileScanner {

    private const val MIN_SIZE = 1024L // ignore files smaller than 1 KB
    private const val PARTIAL = 64 * 1024L

    /**
     * Step 1: group by size. Step 2: group by hash of the first 64 KB.
     * Step 3: confirm with a full SHA-256. Only files that match on all
     * three are reported as duplicates.
     */
    fun scan(
        root: File = Environment.getExternalStorageDirectory(),
        onProgress: (Int) -> Unit = {}
    ): List<DupGroup> {
        val bySize = HashMap<Long, MutableList<File>>()
        var seen = 0

        root.walkTopDown()
            .onEnter { dir -> !dir.name.startsWith(".") }
            .forEach { f ->
                if (f.isFile) {
                    val len = f.length()
                    if (len >= MIN_SIZE) {
