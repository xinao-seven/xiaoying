package com.xiaoying.pipeline;

import com.xiaoying.storage.StorageService;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * 用 Java2D 把 N 张素材拼成 1200×800 PNG。不是真实 AI，只用于形成可预览的结果闭环。
 */
@Component
public class Java2dResultComposer implements ResultComposer {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 800;
    private static final int CAPTION_HEIGHT = 120;
    private static final int CONTENT_HEIGHT = HEIGHT - CAPTION_HEIGHT;
    private static final int GAP = 8;
    private static final String FALLBACK_CAPTION = "MY CREATION";
    private static final String[] PREFERRED_FONTS = {
            "Noto Sans CJK SC", "Source Han Sans SC", "Microsoft YaHei", "SimHei",
            "WenQuanYi Zen Hei", "SansSerif"
    };

    private final StorageService storageService;

    public Java2dResultComposer(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public BufferedImage compose(List<Path> materialPaths, String caption) {
        List<BufferedImage> images = new ArrayList<>(materialPaths.size());
        for (int index = 0; index < materialPaths.size(); index++) {
            try {
                BufferedImage image = ImageIO.read(materialPaths.get(index).toFile());
                if (image == null) {
                    throw new StageFailure("MATERIAL_UNREADABLE",
                            "素材检查失败：第 " + (index + 1) + " 张图片无法解码");
                }
                images.add(image);
            } catch (IOException ex) {
                throw new StageFailure("MATERIAL_UNREADABLE",
                        "素材检查失败：第 " + (index + 1) + " 张图片无法解码");
            }
        }
        if (images.isEmpty()) {
            throw new StageFailure("MATERIAL_NOT_FOUND", "素材检查失败：没有可用素材");
        }

        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setPaint(new GradientPaint(0, 0, new Color(18, 24, 38),
                    WIDTH, HEIGHT, new Color(48, 32, 64)));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            List<Region> regions = layout(images.size());
            for (int index = 0; index < images.size() && index < regions.size(); index++) {
                drawCover(graphics, images.get(index), regions.get(index));
            }
            drawCaption(graphics, caption);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    @Override
    public StoredResult store(BufferedImage image, String taskId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("PNG 编码失败");
        }
        String key = storageService.storeResult(taskId, out.toByteArray());
        return new StoredResult(key, "/api/tasks/" + taskId + "/result",
                image.getWidth(), image.getHeight());
    }

    private static void drawCover(Graphics2D graphics, BufferedImage image, Region region) {
        int x = region.x() + GAP;
        int y = region.y() + GAP;
        int width = region.width() - GAP * 2;
        int height = region.height() - GAP * 2;
        if (width <= 0 || height <= 0) {
            return;
        }
        double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
        int sourceWidth = Math.min(image.getWidth(), Math.max(1, (int) Math.ceil(width / scale)));
        int sourceHeight = Math.min(image.getHeight(), Math.max(1, (int) Math.ceil(height / scale)));
        int sourceX = (image.getWidth() - sourceWidth) / 2;
        int sourceY = (image.getHeight() - sourceHeight) / 2;

        graphics.setColor(new Color(12, 16, 26));
        graphics.fillRoundRect(x - 2, y - 2, width + 4, height + 4, 12, 12);
        graphics.drawImage(image, x, y, x + width, y + height,
                sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight, null);
    }

    private static void drawCaption(Graphics2D graphics, String caption) {
        graphics.setColor(new Color(10, 14, 24, 210));
        graphics.fillRect(0, CONTENT_HEIGHT, WIDTH, CAPTION_HEIGHT);
        graphics.setColor(new Color(255, 255, 255, 40));
        graphics.drawLine(0, CONTENT_HEIGHT, WIDTH, CONTENT_HEIGHT);

        String text = caption == null || caption.isBlank() ? FALLBACK_CAPTION : caption.trim();
        String family = pickFontFamily(text);
        if (family == null) {
            text = FALLBACK_CAPTION;
            family = "SansSerif";
        }
        Font font = new Font(family, Font.BOLD, 44);
        graphics.setFont(font);
        graphics.setColor(new Color(245, 247, 252));
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        int x = Math.max(24, (WIDTH - textWidth) / 2);
        int y = CONTENT_HEIGHT + (CAPTION_HEIGHT + graphics.getFontMetrics().getAscent()
                - graphics.getFontMetrics().getDescent()) / 2;
        graphics.drawString(text, x, y);
    }

    private static String pickFontFamily(String text) {
        Set<String> available = new LinkedHashSet<>(
                List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String preferred : PREFERRED_FONTS) {
            if (available.contains(preferred) && new Font(preferred, Font.BOLD, 44).canDisplayUpTo(text) == -1) {
                return preferred;
            }
        }
        for (String name : available) {
            if (new Font(name, Font.BOLD, 44).canDisplayUpTo(text) == -1) {
                return name;
            }
        }
        return null;
    }

    private static List<Region> layout(int count) {
        if (count <= 1) {
            return List.of(new Region(0, 0, WIDTH, CONTENT_HEIGHT));
        }
        if (count == 2) {
            int half = WIDTH / 2;
            return List.of(
                    new Region(0, 0, half, CONTENT_HEIGHT),
                    new Region(half, 0, WIDTH - half, CONTENT_HEIGHT));
        }
        if (count == 3) {
            int leftWidth = (int) Math.round(WIDTH * 0.62);
            int rightWidth = WIDTH - leftWidth;
            int topHeight = CONTENT_HEIGHT / 2;
            return List.of(
                    new Region(0, 0, leftWidth, CONTENT_HEIGHT),
                    new Region(leftWidth, 0, rightWidth, topHeight),
                    new Region(leftWidth, topHeight, rightWidth, CONTENT_HEIGHT - topHeight));
        }
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil(count / (double) columns);
        int cellWidth = WIDTH / columns;
        int cellHeight = CONTENT_HEIGHT / rows;
        List<Region> regions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int column = index % columns;
            int x = column * cellWidth;
            int y = row * cellHeight;
            int width = column == columns - 1 ? WIDTH - x : cellWidth;
            int height = row == rows - 1 ? CONTENT_HEIGHT - y : cellHeight;
            regions.add(new Region(x, y, width, height));
        }
        return regions;
    }

    private record Region(int x, int y, int width, int height) {
    }
}