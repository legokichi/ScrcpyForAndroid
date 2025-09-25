package org.client.scrcpy.utils;

import android.content.Context;
import android.os.Build;


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

//@SuppressWarnings("ALL")
public class FileUtils {
    private FileUtils() {
    }

    public static String getAssetsData(Context context, String fileName) {
        String result = "";
        try {
            // Obtain the input stream
            InputStream mAssets = context.getAssets().open(fileName);

            // Determine the file length in bytes
            int lenght = mAssets.available();
            // Allocate a byte array
            byte[] buffer = new byte[lenght];
            // Read the file contents into the byte array
            mAssets.read(buffer);
            mAssets.close();
            result = new String(buffer);
            return result;
        } catch (IOException e) {
            return result;
        }
    }

    public static byte[] getAssetsBytes(Context context, String fileName) {
        try {
            // Obtain the input stream
            InputStream mAssets = context.getAssets().open(fileName);
            // Determine the file length in bytes
            int lenght = mAssets.available();
            // Allocate a byte array
            byte[] buffer = new byte[lenght];
            // Read the file contents into the byte array
            mAssets.read(buffer);
            mAssets.close();
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Copy an entire directory from assets into /data/data/<package>/files/.
     *
     * @param context activity using the CopyFiles helper
     * @param filePath source path such as /assets/aa
     */
    public static boolean copyAssetsDir2Phone(Context context, String filePath, String toPath) {

        String[] fileList = null;
        try {
            fileList = context.getAssets().list(filePath);
        } catch (IOException ignore) {
        }
        if (fileList != null && fileList.length > 0) { // Handle directory entries
            boolean suc = true;
            for (String fileName : fileList) {
                String nextFilePath = filePath + File.separator + fileName;
                suc = copyAssetsDir2Phone(context, nextFilePath, toPath) && suc;
            }
            return suc;
        } else { // Handle file entries
            InputStream inputStream = null;
            FileOutputStream fos = null;
            try {
                inputStream = context.getAssets().open(filePath);
                File file = new File(toPath + File.separator + filePath);
                if (file.exists()) {
                    if (!deleteFileSafely(file)) return false;
                } else {
                    File parentFile = file.getParentFile();
                    if ((!parentFile.exists()) && (!parentFile.mkdirs())) return false;
                }
                fos = new FileOutputStream(file);
                int len;
                byte[] buffer = new byte[2048];
                while ((len = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fos != null) {
                        fos.flush();
                        fos.close();
                    }
                    if (inputStream != null) inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }
    }

    public boolean copyFileToPath(String fromfile, String tofile) {
        File sourceFile = new File(fromfile);
        if (!sourceFile.exists() || !sourceFile.canRead()) { // Ensure we have permission and the file exists
            return false;
        }
        if (sourceFile.isDirectory()) { // Recursively handle directories
            File[] files = sourceFile.listFiles();
            boolean suc = true;
            for (File file : files) {
                suc = !copyFileToPath(file.getAbsolutePath(), tofile + File.separator + file.getName()) && suc;
            }
            return suc;
        } else {
            return copyFile(fromfile, tofile); // For files, copy directly
        }
    }

    public static boolean copyFile(String path, String toPath) {
        File file = new File(path);
        File toFile = new File(toPath);
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        if (!file.exists() || !file.canRead()) { // Ensure we have permission and the file exists
            return false;
        }
        if (!toFile.exists()) { // Create the destination if it does not exist
            try {
                File dir = new File(toFile.getParent());
                if ((!dir.exists()) && !dir.mkdirs()) return false;
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        if (toFile.exists() && !deleteFileSafely(toFile)) return false;

        try {
            inputStream = new FileInputStream(file);
            outputStream = new FileOutputStream(toFile);
            byte[] buff = new byte[2048];
            int index;
            while ((index = inputStream.read(buff)) > 0) {
                outputStream.write(buff, 0, index);
            }
            return true;
        } catch (IOException e) {
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
                if (inputStream != null) inputStream.close();
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Merge two files into a new target file.
     *
     * @param path path to the first source file
     * @param toPath destination path for the merged file
     * @return {@code true} on success, {@code false} otherwise
     */
    public static boolean multiFile(String path, String path2, String toPath) {
        File file = new File(path);
        File file2 = new File(path2);
        File toFile = new File(toPath);
        FileInputStream inputStream = null;
        FileInputStream inputStream2 = null;
        FileOutputStream outputStream = null;
        if (!file.exists() || !file.canRead()) { // Ensure we have permission and the file exists
            return false;
        }
        if (!file2.exists() || !file2.canRead()) { // Ensure we have permission and the file exists
            return false;
        }
        if (!toFile.exists()) { // Create the destination if it does not exist
            try {
                File dir = new File(toFile.getParent());
                if ((!dir.exists()) && !dir.mkdirs()) return false;
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        if (toFile.exists() && !deleteFileSafely(toFile)) return false;

        try {
            inputStream = new FileInputStream(file);
            inputStream2 = new FileInputStream(file2);
            outputStream = new FileOutputStream(toFile);
            byte[] buff = new byte[2048];
            int index;
            while ((index = inputStream.read(buff)) > 0) {
                outputStream.write(buff, 0, index);
            }
            while ((index = inputStream2.read(buff)) > 0) {
                outputStream.write(buff, 0, index);
            }
            return true;
        } catch (IOException e) {
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (inputStream2 != null) {
                    inputStream2.close();
                }
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static byte[] readFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists())
            return null;
        byte[] retBytes = new byte[(int) file.length()];
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            in.read(retBytes);
            return retBytes;
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String readFileToString(String filePath) {
        File file = new File(filePath);
        if (!file.exists())
            return null;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            StringBuilder readStringBuilder = new StringBuilder();
            String currentLine;
            boolean start = true;
            while ((currentLine = in.readLine()) != null) {
                if (!start) {
                    readStringBuilder.append("\n");
                } else {
                    start = false;
                }
                readStringBuilder.append(currentLine);
            }
            return readStringBuilder.toString();
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean putStringToFile(String path, String text) {
        File file = new File(path);
        if (file.exists() && (!deleteFileSafely(file)))
            return false;
        if (file.getParentFile() != null
                && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(file), 2048);
            out.write(text);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean putBytes(String path, byte[] text) {
        File file = new File(path);
        if (file.exists() && (!deleteFileSafely(file)))
            return false;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(text);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @param file file to delete
     */

    public static boolean deleteFileSafely(File file) {
        if (file != null && file.exists()) {
            if (file.isDirectory()) {  // Recursively delete contents if it is a directory
                File[] files = file.listFiles();
                if (files != null && files.length > 0) {
                    for (File file1 : files) {
                        deleteFileSafely(file1);
                    }
                }
            }
            File tmp = getTmpFile(file, System.currentTimeMillis(), -1);
            if (file.renameTo(tmp)) { // Rename the source file
                return tmp.delete(); // Delete the renamed file
            } else {
                return file.delete();
            }
        }
        return false;
    }

    private static File getTmpFile(File file, long time, int index) {
        File tmp;
        if (index == -1) {
            tmp = new File(file.getParent() + File.separator + time);
        } else {
            tmp = new File(file.getParent() + File.separator + time + "(" + index + ")");
        }
        if (!tmp.exists()) {
            return tmp;
        } else {
            return getTmpFile(file, time, index >= 1000 ? index : ++index);
        }
    }


    /**
     * Compress a file or directory into a zip archive.
     *
     * @param srcPath source file or directory
     * @param dstPath output archive
     */
    public static boolean compressToZip(String srcPath, String dstPath) {
        File srcFile = new File(srcPath);
        File dstFile = new File(dstPath);
        if (!srcFile.exists()) {
            System.out.println(srcPath + " does not exist ！");
            return false;
        }

        try (FileOutputStream out = new FileOutputStream(dstFile);
             ZipOutputStream zipOut = new ZipOutputStream(new CheckedOutputStream(out, new CRC32()))) {
            String baseDir = "";
            compress(srcFile, zipOut, baseDir, true);

            // Close the entry
            zipOut.closeEntry();

            return true;
        } catch (IOException e) {
            System.out.println(" compress exception = " + e.getMessage());
            return false;
        }
    }


    /**
     * Extract a zip archive.
     *
     * @param zipPath archive to unpack
     * @param descDir destination directory
     * @return {@code true} on success, {@code false} otherwise
     */
    @SuppressWarnings("rawtypes")
    public static boolean decompressZip(String zipPath, String descDir) {
        File zipFile = new File(zipPath);
        boolean flag = false;
        if (!descDir.endsWith(File.separator)) {
            descDir = descDir + File.separator;
        }
        File pathFile = new File(descDir);
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }

        ZipFile zip = null;
        try {
            // This method is available only on API level 24 and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                zip = new ZipFile(zipFile, Charset.forName("gbk")); // Prevent garbled paths when directories contain Chinese characters
            } else {
                zip = new ZipFile(zipFile);
            }
            for (Enumeration entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                String zipEntryName = entry.getName();
                InputStream in = zip.getInputStream(entry);

                // Compose the output path from the destination directory and the entry name
                String outPath = (descDir + zipEntryName).replace("/", File.separator);
                // Ensure the output directory exists
                File file = new File(outPath.substring(0, outPath.lastIndexOf(File.separator)));

                if (!file.exists()) {
                    file.mkdirs();
                }
                // Skip extraction when the entry resolves to a directory, since it was already created
                if (new File(outPath).isDirectory()) {
                    try {
                        in.close();
                    } catch (Exception ignore) {
                    }
                    continue;
                }

                // Track extracted file paths (useful when leveraging md5.zip naming to detect repeats)
//                System.err.println("Zip extracted to: " + outPath);
                //noinspection IOStreamConstructor
                OutputStream out = new FileOutputStream(outPath);
                byte[] buf1 = new byte[2048];
                int len;
                while ((len = in.read(buf1)) > 0) {
                    out.write(buf1, 0, len);
                }
                try {
                    in.close();
                } catch (Exception ignore) {
                }
                try {
                    out.close();
                } catch (Exception ignore) {
                }
            }
            flag = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return flag;
    }

    private static void compress(File file, ZipOutputStream zipOut, String baseDir, boolean isRootDir) throws IOException {
        if (file.isDirectory()) {
            compressDirectory(file, zipOut, baseDir, isRootDir);
        } else {
            compressFile(file, zipOut, baseDir);
        }
    }

    /**
     * Compress a directory.
     */
    private static void compressDirectory(File dir, ZipOutputStream zipOut, String baseDir, boolean isRootDir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {  // These lines ensure empty directories are preserved
            ZipEntry entry = new ZipEntry(baseDir + dir.getName() + "/");
            zipOut.putNextEntry(entry);
            return;
        }
        for (File file : files) {
            String compressBaseDir = "";
            if (!isRootDir) {
                compressBaseDir = baseDir + dir.getName() + "/";
            }
            compress(file, zipOut, compressBaseDir, false);
        }
    }

    /**
     * Compress a file.
     */
    private static void compressFile(File file, ZipOutputStream zipOut, String baseDir) throws IOException {
        if (!file.exists()) {
            return;
        }

        //noinspection IOStreamConstructor
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            String fileName = file.getName();
            ZipEntry entry = new ZipEntry(baseDir + fileName);
            zipOut.putNextEntry(entry);
            int count;
            byte[] data = new byte[2048];
            while ((count = bis.read(data, 0, 2048)) != -1) {
                zipOut.write(data, 0, count);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
