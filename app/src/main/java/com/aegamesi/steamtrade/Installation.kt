package com.aegamesi.steamtrade

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

object Installation {
    private const val INSTALLATION = "INSTALLATION"
    private var sID: String? = null

    @Synchronized
    fun id(): String {
        if (sID == null) {
            val installation = File(SteamTrade.filesDirectory, INSTALLATION)
            try {
                if (!installation.exists())
                    writeInstallationFile(installation)
                sID = readInstallationFile(installation)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

        }
        return sID!!
    }

    @Throws(IOException::class)
    private fun writeInstallationFile(installation: File) {
        val out = FileOutputStream(installation)
        val id = UUID.randomUUID().toString()
        out.write(id.toByteArray())
        out.close()
    }

    @Throws(IOException::class)
    private fun readInstallationFile(installation: File): String {
        val f = RandomAccessFile(installation, "r")
        val bytes = ByteArray(f.length().toInt())
        f.readFully(bytes)
        f.close()
        return String(bytes)
    }
}