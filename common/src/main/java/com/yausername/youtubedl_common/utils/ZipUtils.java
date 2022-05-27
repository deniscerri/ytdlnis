package com.yausername.youtubedl_common.utils;

import android.system.ErrnoException;
import android.system.Os;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;


public class ZipUtils {

    private ZipUtils() {
    }

    public static void unzip(File sourceFile, File targetDirectory) throws IOException, ErrnoException, IllegalAccessException {
        try (ZipFile zipFile = new ZipFile(sourceFile)) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File entryDestination = new File(targetDirectory, entry.getName());
                // prevent zipSlip
                if (!entryDestination.getCanonicalPath().startsWith(targetDirectory.getCanonicalPath() + File.separator)) {
                    throw new IllegalAccessException("Entry is outside of the target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    entryDestination.mkdirs();
                } else if (entry.isUnixSymlink()) {
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        String symlink = IOUtils.toString(in, StandardCharsets.UTF_8);
                        Os.symlink(symlink, entryDestination.getAbsolutePath());
                    }
                } else {
                    entryDestination.getParentFile().mkdirs();
                    try (InputStream in = zipFile.getInputStream(entry);
                         OutputStream out = new FileOutputStream(entryDestination)) {
                        IOUtils.copy(in, out);
                    }
                }
            }
        }
    }

    public static void unzip(InputStream inputStream, File targetDirectory) throws IOException, IllegalAccessException {
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new BufferedInputStream(inputStream))) {
            ZipArchiveEntry entry = null;
            while ((entry = zis.getNextZipEntry()) != null) {
                File entryDestination = new File(targetDirectory, entry.getName());
                // prevent zipSlip
                if (!entryDestination.getCanonicalPath().startsWith(targetDirectory.getCanonicalPath() + File.separator)) {
                    throw new IllegalAccessException("Entry is outside of the target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    entryDestination.mkdirs();
                } else {
                    entryDestination.getParentFile().mkdirs();
                    try (OutputStream out = new FileOutputStream(entryDestination)) {
                        IOUtils.copy(zis, out);
                    }
                }

            }
        }
    }

}
