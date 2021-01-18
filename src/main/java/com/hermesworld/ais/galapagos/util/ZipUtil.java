package com.hermesworld.ais.galapagos.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ZipUtil {

    private ZipUtil() {
    }

    public static byte[] zipFiles(String[] fileNames, byte[][] fileContents) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(baos);

        try {
            for (int i = 0; i < fileNames.length; i++) {
                ZipEntry ze = new ZipEntry(fileNames[i]);
                zip.putNextEntry(ze);
                zip.write(fileContents[i]);
                zip.closeEntry();
            }

            zip.close();
            return baos.toByteArray();
        }
        catch (IOException e) {
            // should not occur for memory-only operations
            throw new RuntimeException(e);
        }
    }

}
