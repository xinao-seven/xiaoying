package com.xiaoying.material;

/**
 * 基于文件头的图片类型识别，不信任客户端 Content-Type。
 */
public final class FileHeader {

    public enum ImageFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png");

        private final String mimeType;
        private final String extension;

        ImageFormat(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        public String mimeType() {
            return mimeType;
        }

        public String extension() {
            return extension;
        }
    }

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private FileHeader() {
    }

    public static ImageFormat detect(byte[] data) {
        if (data == null) {
            return null;
        }
        if (startsWith(data, PNG_MAGIC)) {
            return ImageFormat.PNG;
        }
        if (startsWith(data, JPEG_MAGIC)) {
            return ImageFormat.JPEG;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}