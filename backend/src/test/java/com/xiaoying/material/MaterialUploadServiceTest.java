package com.xiaoying.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.config.AppProperties;
import com.xiaoying.storage.StorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** 覆盖文件头检测、解码校验、落盘与归属；使用临时目录与内存 mapper。 */
class MaterialUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    @TempDir
    Path tempDir;

    private final FakeMaterialMapper mapper = new FakeMaterialMapper();
    private AppProperties properties;
    private StorageService storageService;
    private MaterialService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.setDataDir(tempDir);
        storageService = new StorageService(properties);
        service = new MaterialService(mapper, storageService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void uploadsPngAndPersistsRow() throws IOException {
        byte[] bytes = imageBytes("png", 640, 480, "png");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", bytes);

        MaterialDto dto = service.upload("usr_demo", file);

        assertThat(dto.id()).startsWith("mat_").hasSize(30);
        assertThat(dto.mimeType()).isEqualTo("image/png");
        assertThat(dto.width()).isEqualTo(640);
        assertThat(dto.height()).isEqualTo(480);
        assertThat(dto.sizeBytes()).isEqualTo(bytes.length);
        assertThat(dto.url()).isEqualTo("/api/materials/" + dto.id() + "/content");
        assertThat(dto.createdAt().getNano() % 1_000_000).isZero();

        MaterialRow row = mapper.rows.get(dto.id());
        assertThat(row).isNotNull();
        assertThat(row.getUserId()).isEqualTo("usr_demo");
        assertThat(row.getStatus()).isEqualTo("UPLOADED");
        assertThat(row.getStorageKey()).startsWith("uploads/usr_demo/").endsWith(".png");
        assertThat(Files.exists(storageService.resolve(row.getStorageKey()))).isTrue();
    }

    @Test
    void detectsJpegByMagicBytesEvenWhenContentTypeLies() throws IOException {
        byte[] bytes = imageBytes("jpg", 120, 80, "jpg");
        MockMultipartFile file = new MockMultipartFile("file", "photo.txt", "text/plain", bytes);

        MaterialDto dto = service.upload("usr_demo", file);

        assertThat(dto.mimeType()).isEqualTo("image/jpeg");
        assertThat(mapper.rows.get(dto.id()).getStorageKey()).endsWith(".jpg");
    }

    @Test
    void rejectsFakePngTextFileWith415() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "not an image at all".getBytes());

        assertThatThrownBy(() -> service.upload("usr_demo", file))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.MATERIAL_MIME_NOT_ALLOWED);
        assertThat(mapper.rows).isEmpty();
    }

    @Test
    void rejectsPngHeaderThatCannotBeDecodedWith400() {
        byte[] corrupted = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "broken.png", "image/png", corrupted);

        assertThatThrownBy(() -> service.upload("usr_demo", file))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.MATERIAL_UNREADABLE);
        assertThat(mapper.rows).isEmpty();
    }

    @Test
    void rejectsFileOverGlobalLimitWith413() throws IOException {
        properties.getUpload().setMaxBytes(32);
        byte[] bytes = imageBytes("png", 64, 64, "png");
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", bytes);

        assertThatThrownBy(() -> service.upload("usr_demo", file))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rejectsEmptyUpload() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload("usr_demo", file))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void otherUserCannotReadMaterial() {
        MaterialRow row = new MaterialRow();
        row.setId("mat_01");
        row.setUserId("usr_demo");
        row.setStorageKey("uploads/usr_demo/x.png");
        row.setMimeType("image/png");
        mapper.rows.put(row.getId(), row);

        assertThatThrownBy(() -> service.loadForUser("mat_01", "usr_alice"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.MATERIAL_NOT_FOUND);
    }

    @Test
    void ownerGets404WhenFileIsMissing() {
        MaterialRow row = new MaterialRow();
        row.setId("mat_02");
        row.setUserId("usr_demo");
        row.setStorageKey("uploads/usr_demo/missing.png");
        row.setMimeType("image/png");
        mapper.rows.put(row.getId(), row);

        assertThatThrownBy(() -> service.loadForUser("mat_02", "usr_demo"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.MATERIAL_NOT_FOUND);
    }

    @Test
    void removesStoredFileWhenInsertFails() throws IOException {
        mapper.failOnInsert = true;
        byte[] bytes = imageBytes("png", 64, 64, "png");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", bytes);

        assertThatThrownBy(() -> service.upload("usr_demo", file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        Path userDir = tempDir.resolve("uploads").resolve("usr_demo");
        assertThat(Files.exists(userDir)).isTrue();
        try (Stream<Path> entries = Files.list(userDir)) {
            assertThat(entries.count()).isZero();
        }
        assertThat(mapper.rows).isEmpty();
    }

    private static byte[] imageBytes(String formatName, int width, int height, String fileName)
            throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, out);
        return out.toByteArray();
    }

    static final class FakeMaterialMapper implements MaterialMapper {

        final Map<String, MaterialRow> rows = new HashMap<>();
        boolean failOnInsert;

        @Override
        public void insert(MaterialRow row) {
            if (failOnInsert) {
                throw new IllegalStateException("database unavailable");
            }
            rows.put(row.getId(), row);
        }

        @Override
        public MaterialRow findById(String id) {
            return rows.get(id);
        }
    }
}