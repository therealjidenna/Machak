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
                        bySize.getOrPut(len) { mutableListOf() }.add(f)
                    }
                    seen++
                    if (seen % 200 == 0) onProgress(seen)
                }
            }
        onProgress(seen)

        val result = mutableListOf<DupGroup>()

        for ((size, sameSize) in bySize) {
            if (sameSize.size < 2) continue

            val byPartial = HashMap<String, MutableList<File>>()
            for (f in sameSize) {
                val h = hash(f, PARTIAL) ?: continue
                byPartial.getOrPut(h) { mutableListOf() }.add(f)
            }

            for (candidates in byPartial.values) {
                if (candidates.size < 2) continue

                val byFull = HashMap<String, MutableList<File>>()
                for (f in candidates) {
                    val h = hash(f, Long.MAX_VALUE) ?: continue
                    byFull.getOrPut(h) { mutableListOf() }.add(f)
                }

                for (same in byFull.values) {
                    if (same.size >= 2) {
                        result.add(
                            DupGroup(
                                size,
                                same.map { DupFile(it.absolutePath, size, it.lastModified()) }
                            )
                        )
                    }
                }
            }
        }

        return result.sortedByDescending { it.size * (it.files.size - 1) }
    }

    private fun hash(f: File, limit: Long): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { s ->
                val buf = ByteArray(64 * 1024)
                var left = limit
                while (left > 0) {
                    val want = minOf(buf.size.toLong(), left).toInt()
                    val n = s.read(buf, 0, want)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    left -= n
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}

object AppScanner {

    /** Apps that share the same name (e.g. cloned or duplicate installs). */
    fun scan(ctx: Context): List<List<AppItem>> {
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                val version = try {
                    pm.getPackageInfo(it.packageName, 0).versionName ?: ""
                } catch (e: Exception) {
                    ""
                }
                AppItem(pm.getApplicationLabel(it).toString(), it.packageName, version)
            }

        return apps
            .groupBy { it.label.trim().lowercase() }
            .values
            .filter { it.size > 1 }
            .toList()
    }
}
