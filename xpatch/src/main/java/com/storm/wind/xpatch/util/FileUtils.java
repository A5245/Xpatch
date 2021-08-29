package com.storm.wind.xpatch.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Wind
 */
public class FileUtils {

    static final int BUFFER = 8192;

    /**
     * 解压文件
     *
     * @param zipPath 要解压的目标文件
     * @param descDir 指定解压目录
     * @return 解压结果：成功，失败
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
            try {
                // api level 24 才有此方法
                zip = new ZipFile(zipFile, Charset.forName("gbk"));//防止中文目录，乱码
            } catch (NoSuchMethodError e) {
                // api < 24
                zip = new ZipFile(zipFile);
            }
            for (Enumeration entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                String zipEntryName = entry.getName();
                InputStream in = zip.getInputStream(entry);

                //指定解压后的文件夹+当前zip文件的名称
                String outPath = (descDir + zipEntryName).replace("/", File.separator);
                //判断路径是否存在,不存在则创建文件路径
                File file = new File(outPath.substring(0, outPath.lastIndexOf(File.separator)));

                if (!file.exists()) {
                    file.mkdirs();
                }
                //判断文件全路径是否为文件夹,如果是上面已经上传,不需要解压
                if (new File(outPath).isDirectory()) {
                    continue;
                }
                //保存文件路径信息（可利用md5.zip名称的唯一性，来判断是否已经解压）
//                System.err.println("当前zip解压之后的路径为：" + outPath);
                OutputStream out = new FileOutputStream(outPath);
                byte[] buf1 = new byte[2048];
                int len;
                while ((len = in.read(buf1)) > 0) {
                    out.write(buf1, 0, len);
                }
                close(in);
                close(out);
            }
            flag = true;
            close(zip);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return flag;
    }

    private static InputStream getInputStreamFromFile(String filePath) {
        return FileUtils.class.getClassLoader().getResourceAsStream(filePath);
    }

    // copy an asset file into a path
    public static void copyFileFromJar(String inJarPath, String distPath) {

//        System.out.println("start copyFile  inJarPath =" + inJarPath + "  distPath = " + distPath);
        InputStream inputStream = getInputStreamFromFile(inJarPath);

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new BufferedInputStream(inputStream);
            out = new BufferedOutputStream(new FileOutputStream(distPath));

            int len = -1;
            byte[] b = new byte[1024];
            while ((len = in.read(b)) != -1) {
                out.write(b, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(out);
            close(in);
        }
    }

    public static void copyFile(String sourcePath, String targetPath) {
        copyFile(new File(sourcePath), new File(targetPath));
    }

    public static void copyFile(File source, File target) {

        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(target);
            FileChannel iChannel = inputStream.getChannel();
            FileChannel oChannel = outputStream.getChannel();

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (true) {
                buffer.clear();
                int r = iChannel.read(buffer);
                if (r == -1) {
                    break;
                }
                buffer.limit(buffer.position());
                buffer.position(0);
                oChannel.write(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(inputStream);
            close(outputStream);
        }
    }

    public static void deleteDir(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    deleteDir(f);
                }
            }
        }
        file.delete();
    }

    public static void compressToZip(String srcPath, String dstPath, String originZipPath) {
        File srcFile = new File(srcPath);
        File dstFile = new File(dstPath);
        if (!srcFile.exists()) {
            System.out.println(srcPath + " does not exist ！");
            return;
        }

        FileOutputStream out = null;
        ZipOutputStream zipOut = null;
        try {
            out = new FileOutputStream(dstFile);
            CheckedOutputStream cos = new CheckedOutputStream(out, new CRC32());
            zipOut = new ZipOutputStream(cos);
            String baseDir = "";
            compress(srcFile, zipOut, baseDir, true, originZipPath);
        } catch (IOException e) {
            System.out.println(" compress exception = " + e.getMessage());
        } finally {
            try {
                if (zipOut != null) {
                    zipOut.closeEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            close(zipOut);
            close(out);
        }
    }

    private static void compress(File file, ZipOutputStream zipOut, String baseDir, boolean isRootDir, String originZipPath) throws IOException {
        if (file.isDirectory()) {
            compressDirectory(file, zipOut, baseDir, isRootDir, originZipPath);
        } else {
            compressFile(file, zipOut, baseDir, originZipPath);
        }
    }

    /**
     * 压缩一个目录
     */
    private static void compressDirectory(File dir, ZipOutputStream zipOut, String baseDir, boolean isRootDir, String originZipPath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            String compressBaseDir = "";
            if (!isRootDir) {
                compressBaseDir = baseDir + dir.getName() + "/";
            }
            compress(files[i], zipOut, compressBaseDir, false, originZipPath);
        }
    }

    /**
     * 压缩一个文件
     */
    private static void compressFile(File file, ZipOutputStream zipOut, String baseDir, String originZipPath) throws IOException {
        if (!file.exists()) {
            return;
        }

        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            ZipEntry entry = new ZipEntry(baseDir + file.getName());
            if (file.getName().contains("resources.arsc")) {
                ZipEntry originEntry = getZipEntryFromZipFile(originZipPath, file.getName());
                System.out.println(" file name : " + file.getName() + " originEntry = " + originEntry);
                if (originEntry != null) {
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(originEntry.getSize());
                    entry.setCompressedSize(originEntry.getCompressedSize());
                    entry.setCrc(originEntry.getCrc());
                }
            }
            zipOut.putNextEntry(entry);
            int count;
            byte data[] = new byte[BUFFER];
            while ((count = bis.read(data, 0, BUFFER)) != -1) {
                zipOut.write(data, 0, count);
            }

        } finally {
            if (null != bis) {
                bis.close();
            }
        }
    }

    private static ZipEntry getZipEntryFromZipFile(String zipPath, String fileName) {
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new FileInputStream(zipPath));

            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                if (name.equals(fileName)) {
                    return entry;
                }
                entry = zin.getNextEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zin != null) {
                try {
                    zin.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static void writeFile(String filePath, String content) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        if (content == null || content.isEmpty()) {
            return;
        }

        File dstFile = new File(filePath);

        if (!dstFile.getParentFile().exists()) {
            dstFile.getParentFile().mkdirs();
        }

        FileOutputStream outputStream = null;
        BufferedWriter writer = null;
        try {
            outputStream = new FileOutputStream(dstFile);
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(content);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(outputStream);
            close(writer);
        }
    }

    private static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

}
