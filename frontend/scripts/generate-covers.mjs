#!/usr/bin/env node
/**
 * 生成 6 张示例模板封面（自绘渐变 + 几何图形，无第三方素材）。
 * 输出：frontend/public/demo/*.png
 * 用法：npm run covers
 */
import { deflateSync } from "node:zlib";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const WIDTH = 720;
const HEIGHT = 540;
const OUT_DIR = join(
  dirname(fileURLToPath(import.meta.url)),
  "..",
  "public",
  "demo",
);

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuffer = Buffer.from(type, "ascii");
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])), 0);
  return Buffer.concat([length, typeBuffer, data, crc]);
}

function encodePng(pixels) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(WIDTH, 0);
  ihdr.writeUInt32BE(HEIGHT, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // truecolor RGB
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const stride = WIDTH * 3;
  const raw = Buffer.alloc((stride + 1) * HEIGHT);
  for (let y = 0; y < HEIGHT; y += 1) {
    raw[y * (stride + 1)] = 0; // filter: none
    pixels.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }

  return Buffer.concat([
    signature,
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

function createCanvas() {
  const pixels = Buffer.alloc(WIDTH * HEIGHT * 3);
  return { pixels };
}

function setPixel(canvas, x, y, [r, g, b], alpha = 1) {
  if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) return;
  const index = (y * WIDTH + x) * 3;
  const p = canvas.pixels;
  p[index] = Math.round(p[index] * (1 - alpha) + r * alpha);
  p[index + 1] = Math.round(p[index + 1] * (1 - alpha) + g * alpha);
  p[index + 2] = Math.round(p[index + 2] * (1 - alpha) + b * alpha);
}

function mix(a, b, t) {
  return [
    a[0] + (b[0] - a[0]) * t,
    a[1] + (b[1] - a[1]) * t,
    a[2] + (b[2] - a[2]) * t,
  ];
}

function verticalGradient(canvas, top, bottom) {
  for (let y = 0; y < HEIGHT; y += 1) {
    const color = mix(top, bottom, y / (HEIGHT - 1));
    for (let x = 0; x < WIDTH; x += 1) {
      setPixel(canvas, x, y, color);
    }
  }
}

function glow(canvas, cx, cy, radius, color, strength = 0.85) {
  for (let y = Math.max(0, cy - radius); y < Math.min(HEIGHT, cy + radius); y += 1) {
    for (let x = Math.max(0, cx - radius); x < Math.min(WIDTH, cx + radius); x += 1) {
      const distance = Math.hypot(x - cx, y - cy);
      if (distance > radius) continue;
      const falloff = 1 - distance / radius;
      setPixel(canvas, x, y, color, falloff * falloff * strength);
    }
  }
}

function disc(canvas, cx, cy, radius, color, alpha = 1) {
  for (let y = Math.max(0, cy - radius); y < Math.min(HEIGHT, cy + radius); y += 1) {
    for (let x = Math.max(0, cx - radius); x < Math.min(WIDTH, cx + radius); x += 1) {
      if (Math.hypot(x - cx, y - cy) <= radius) {
        setPixel(canvas, x, y, color, alpha);
      }
    }
  }
}

function ring(canvas, cx, cy, radius, thickness, color, alpha = 1) {
  for (let y = Math.max(0, cy - radius - thickness); y < Math.min(HEIGHT, cy + radius + thickness); y += 1) {
    for (let x = Math.max(0, cx - radius - thickness); x < Math.min(WIDTH, cx + radius + thickness); x += 1) {
      const distance = Math.hypot(x - cx, y - cy);
      if (distance <= radius && distance >= radius - thickness) {
        setPixel(canvas, x, y, color, alpha);
      }
    }
  }
}

function rect(canvas, x0, y0, w, h, color, alpha = 1) {
  for (let y = y0; y < y0 + h; y += 1) {
    for (let x = x0; x < x0 + w; x += 1) {
      setPixel(canvas, x, y, color, alpha);
    }
  }
}

function triangle(canvas, cx, baseY, halfWidth, height, color, alpha = 1) {
  for (let row = 0; row < height; row += 1) {
    const y = baseY - row;
    const half = Math.round(halfWidth * (1 - row / height));
    for (let x = cx - half; x <= cx + half; x += 1) {
      setPixel(canvas, x, y, color, alpha);
    }
  }
}

function speckles(canvas, count, seed, color, radius = 3) {
  let state = seed;
  const random = () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 0xffffffff;
  };
  for (let i = 0; i < count; i += 1) {
    disc(
      canvas,
      Math.round(random() * WIDTH),
      Math.round(random() * HEIGHT),
      Math.round(radius * (0.4 + random())),
      color,
      0.35 + random() * 0.4,
    );
  }
}

const covers = {
  "postcard-cover.png": (canvas) => {
    verticalGradient(canvas, [255, 196, 120], [92, 74, 138]);
    glow(canvas, 360, 300, 190, [255, 240, 200], 0.9);
    disc(canvas, 360, 320, 96, [255, 224, 160], 0.92);
    for (let i = 0; i < 4; i += 1) {
      rect(canvas, 0, 400 + i * 34, WIDTH, 16, [30, 42, 66], 0.18 + i * 0.16);
    }
    rect(canvas, 52, 52, 616, 436, [255, 255, 255], 0.06);
  },
  "pet-portrait-cover.png": (canvas) => {
    verticalGradient(canvas, [247, 232, 210], [186, 162, 132]);
    glow(canvas, 300, 260, 230, [255, 250, 240], 0.7);
    disc(canvas, 300, 300, 150, [250, 245, 238], 0.82);
    disc(canvas, 262, 272, 14, [64, 52, 44], 0.9);
    disc(canvas, 338, 272, 14, [64, 52, 44], 0.9);
    disc(canvas, 300, 316, 10, [89, 70, 60], 0.9);
    ring(canvas, 300, 300, 196, 3, [255, 255, 255], 0.35);
    speckles(canvas, 60, 7, [255, 255, 255], 3);
  },
  "holiday-cover.png": (canvas) => {
    verticalGradient(canvas, [16, 54, 44], [8, 24, 20]);
    glow(canvas, 360, 250, 220, [120, 220, 170], 0.35);
    triangle(canvas, 300, 430, 120, 240, [22, 92, 66], 0.95);
    triangle(canvas, 300, 400, 92, 190, [30, 122, 84], 0.95);
    triangle(canvas, 300, 380, 64, 140, [40, 156, 100], 0.95);
    disc(canvas, 300, 232, 16, [255, 214, 120], 0.95);
    disc(canvas, 470, 190, 8, [255, 240, 200], 0.9);
    speckles(canvas, 110, 21, [235, 250, 240], 2.4);
    rect(canvas, 0, 470, WIDTH, 70, [6, 18, 15], 0.5);
  },
  "wedding-cover.png": (canvas) => {
    verticalGradient(canvas, [72, 40, 60], [22, 16, 26]);
    glow(canvas, 360, 250, 240, [255, 214, 224], 0.35);
    ring(canvas, 300, 280, 120, 12, [246, 232, 220], 0.85);
    ring(canvas, 400, 310, 120, 12, [226, 196, 170], 0.85);
    disc(canvas, 350, 295, 8, [255, 246, 240], 0.9);
    speckles(canvas, 80, 41, [255, 236, 240], 2.2);
    rect(canvas, 60, 60, 600, 420, [255, 240, 236], 0.05);
  },
  "city-cover.png": (canvas) => {
    verticalGradient(canvas, [42, 66, 96], [14, 20, 34]);
    glow(canvas, 520, 150, 170, [255, 210, 150], 0.5);
    const bars = [
      [60, 300, 70, 240],
      [150, 350, 54, 190],
      [220, 270, 80, 270],
      [318, 330, 60, 210],
      [396, 240, 74, 300],
      [488, 320, 58, 220],
      [562, 290, 92, 250],
    ];
    for (const [x, y, w, h] of bars) {
      rect(canvas, x, y, w, h, [8, 12, 22], 0.85);
    }
    for (let i = 0; i < 40; i += 1) {
      const x = 64 + ((i * 37) % 580);
      const y = 320 + ((i * 53) % 190);
      rect(canvas, x, y, 4, 4, [255, 220, 150], 0.7);
    }
    rect(canvas, 0, 470, WIDTH, 70, [10, 16, 28], 0.7);
  },
  "pet-family-cover.png": (canvas) => {
    verticalGradient(canvas, [206, 236, 226], [120, 170, 160]);
    glow(canvas, 360, 280, 240, [255, 255, 250], 0.6);
    disc(canvas, 250, 320, 110, [248, 244, 238], 0.9);
    disc(canvas, 430, 300, 122, [236, 230, 222], 0.9);
    disc(canvas, 350, 390, 92, [255, 250, 244], 0.9);
    for (const [x, y] of [
      [214, 296],
      [286, 296],
      [392, 272],
      [468, 272],
      [320, 368],
      [380, 368],
    ]) {
      disc(canvas, x, y, 7, [58, 60, 52], 0.85);
    }
    ring(canvas, 350, 330, 210, 3, [255, 255, 255], 0.3);
  },
};

mkdirSync(OUT_DIR, { recursive: true });

for (const [name, draw] of Object.entries(covers)) {
  const canvas = createCanvas();
  draw(canvas);
  const png = encodePng(canvas.pixels);
  writeFileSync(join(OUT_DIR, name), png);
  console.log(`${name}  ${(png.length / 1024).toFixed(1)} KB`);
}