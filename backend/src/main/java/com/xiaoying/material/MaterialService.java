package com.xiaoying.material;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.common.Ids;
import com.xiaoying.config.AppProperties;
import com.xiaoying.storage.StorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialService {

    public static final String STATUS_UPLOADED = "UPLOADED";
    private static final int MAX_NAME_LENGTH = 255;

    private final MaterialMapper materialMapper;
    private final StorageService storageService;
    private final AppProperties appProperties;
    private final Clock clock;

    public MaterialService(MaterialMapper materialMapper, StorageService storageService,
                           AppProperties appProperties, Clock clock) {
        this.materialMapper = materialMapper;
        this.storageService = storageService;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public MaterialDto upload(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "请选择要上传的图片");
        }
        long maxBytes = appProperties.getUpload().getMaxBytes();
        if (file.getSize() > maxBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "上传文件超过大小限制（最大 " + (maxBytes / 1024 / 1024) + " MB）");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }
        if (bytes.length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "请选择要上传的图片");
        }
        if (bytes.length > maxBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "上传文件超过大小限制（最大 " + (maxBytes / 1024 / 1024) + " MB）");
        }

        FileHeader.ImageFormat format = FileHeader.detect(bytes);
        if (format == null) {
            throw new ApiException(ErrorCode.MATERIAL_MIME_NOT_ALLOWED, "仅支持 JPEG 或 PNG 图片");
        }

        BufferedImage image = decode(bytes);
        if (image == null) {
            throw new ApiException(ErrorCode.MATERIAL_UNREADABLE, "图片无法解码，请更换文件后重试");
        }

        String id = Ids.material();
        String storageKey = storageService.storeUpload(userId, bytes, format.extension());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);

        MaterialRow row = new MaterialRow();
        row.setId(id);
        row.setUserId(userId);
        row.setOriginalName(truncate(file.getOriginalFilename()));
        row.setStorageKey(storageKey);
        row.setMimeType(format.mimeType());
        row.setSizeBytes((long) bytes.length);
        row.setWidth(image.getWidth());
        row.setHeight(image.getHeight());
        row.setStatus(STATUS_UPLOADED);
        row.setCreatedAt(now);
        try {
            materialMapper.insert(row);
        } catch (RuntimeException ex) {
            // 落盘成功但写库失败：删除文件，避免留下无主素材
            storageService.deleteQuietly(storageKey);
            throw ex;
        }

        return new MaterialDto(id, contentUrl(id), format.mimeType(), bytes.length,
                image.getWidth(), image.getHeight(), now);
    }

    public MaterialFile loadForUser(String materialId, String userId) {
        MaterialRow row = materialMapper.findById(materialId);
        if (row == null || !row.getUserId().equals(userId)) {
            // 越权与不存在统一 404，避免泄露资源存在性
            throw new ApiException(ErrorCode.MATERIAL_NOT_FOUND, "素材不存在");
        }
        Path path = storageService.resolve(row.getStorageKey());
        if (!Files.isReadable(path)) {
            throw new ApiException(ErrorCode.MATERIAL_NOT_FOUND, "素材文件不存在");
        }
        return new MaterialFile(row.getMimeType(), path);
    }

    static String contentUrl(String materialId) {
        return "/api/materials/" + materialId + "/content";
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            return null;
        }
    }

    private static String truncate(String name) {
        if (name == null) {
            return null;
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    public record MaterialFile(String mimeType, Path path) {
    }
}