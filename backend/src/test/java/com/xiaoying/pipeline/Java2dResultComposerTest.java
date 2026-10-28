package com.xiaoying.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.xiaoying.config.AppProperties;
import com.xiaoying.storage.StorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** TC-BE-17：结果拼接为 1200×800 PNG，文件真实落盘且可读。 */
class Java2dResultComposerTest {

    @TempDir
    Path tempDir;

    private Java2dResultComposer composer;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir);
        composer = new Java2dResultComposer(new StorageService(properties));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void composesExpectedCanvasSize(int count) throws IOException {
        List<Path> paths = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            paths.add(writePng("image-" + index + ".png", 400 + index * 20, 300, Color.BLUE));
        }

        BufferedImage result = composer.compose(paths, "测试作品");

        assertThat(result.getWidth()).isEqualTo(1200);
        assertThat(result.getHeight()).isEqualTo(800);
    }

    @Test
    void storesPngAndReturnsResultUrl() throws IOException {
        Path path = writePng("single.png", 640, 480, Color.ORANGE);
        BufferedImage image = composer.compose(List.of(path), "夏日旅行");

        ResultComposer.StoredResult stored = composer.store(image, "tsk_result");

        assertThat(stored.url()).isEqualTo("/api/tasks/tsk_result/result");
        assertThat(stored.storageKey()).isEqualTo("results/tsk_result.png");
        assertThat(stored.width()).isEqualTo(1200);
        assertThat(stored.height()).isEqualTo(800);
        Path file = tempDir.resolve(stored.storageKey());
        assertThat(Files.isReadable(file)).isTrue();
        BufferedImage persisted = ImageIO.read(file.toFile());
        assertThat(persisted.getWidth()).isEqualTo(1200);
        assertThat(persisted.getHeight()).isEqualTo(800);
    }

    @Test
    void corruptedMaterialFailsAsUnreadable() throws IOException {
        Path broken = tempDir.resolve("broken.png");
        Files.write(broken, "not an image".getBytes());

        Throwable thrown = catchThrowable(() -> composer.compose(List.of(broken), "x"));

        assertThat(thrown).isInstanceOf(StageFailure.class);
        assertThat(((StageFailure) thrown).code()).isEqualTo("MATERIAL_UNREADABLE");
    }

    private Path writePng(String name, int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        Path path = tempDir.resolve(name);
        ImageIO.write(image, "png", path.toFile());
        return path;
    }
}