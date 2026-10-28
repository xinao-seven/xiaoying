package com.xiaoying.storage;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 本地卷存储。数据库只保存相对 key，所有路径解析都限制在数据目录内。
 */
@Service
public class StorageService {

    private final Path root;

    public StorageService(AppProperties appProperties) {
        this.root = appProperties.getDataDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve("uploads"));
            Files.createDirectories(root.resolve("results"));
        } catch (IOException ex) {
            throw new UncheckedIOException("无法初始化数据目录: " + root, ex);
        }
    }

    public String storeUpload(String userId, byte[] data, String extension) {
        String key = "uploads/" + sanitize(userId) + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入素材文件失败", ex);
        }
        return key;
    }

    public Path resultsDir() {
        Path dir = root.resolve("results");
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new UncheckedIOException("无法创建结果目录", ex);
        }
        return dir;
    }

    public Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "非法的存储路径");
        }
        return path;
    }

    public Path root() {
        return root;
    }

    private String sanitize(String userId) {
        String value = userId == null ? "" : userId.replaceAll("[^A-Za-z0-9_-]", "_");
        return value.isEmpty() ? "anonymous" : value;
    }
}