import { mkdir, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import zlib from 'node:zlib';

const SIZES = [16, 32, 48, 128];

function crc32(buffer) {
  let crc = 0xffffffff;

  for (let index = 0; index < buffer.length; index += 1) {
    crc ^= buffer[index];

    for (let bit = 0; bit < 8; bit += 1) {
      const mask = -(crc & 1);
      crc = (crc >>> 1) ^ (0xedb88320 & mask);
    }
  }

  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuffer = Buffer.from(type, 'ascii');
  const lengthBuffer = Buffer.alloc(4);
  lengthBuffer.writeUInt32BE(data.length, 0);

  const crcBuffer = Buffer.alloc(4);
  crcBuffer.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])), 0);

  return Buffer.concat([lengthBuffer, typeBuffer, data, crcBuffer]);
}

function pngFromRgba(size, rgba) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const stride = size * 4;
  const raw = Buffer.alloc((stride + 1) * size);
  for (let y = 0; y < size; y += 1) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }

  const compressed = zlib.deflateSync(raw);

  return Buffer.concat([
    signature,
    chunk('IHDR', ihdr),
    chunk('IDAT', compressed),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

function setPixel(buffer, size, x, y, [r, g, b, a]) {
  if (x < 0 || y < 0 || x >= size || y >= size) {
    return;
  }

  const offset = (y * size + x) * 4;
  buffer[offset] = r;
  buffer[offset + 1] = g;
  buffer[offset + 2] = b;
  buffer[offset + 3] = a;
}

function blendPixel(buffer, size, x, y, color, alpha = 1) {
  if (x < 0 || y < 0 || x >= size || y >= size) {
    return;
  }

  const offset = (y * size + x) * 4;
  const existingAlpha = buffer[offset + 3] / 255;
  const incomingAlpha = (color[3] / 255) * alpha;
  const nextAlpha = incomingAlpha + existingAlpha * (1 - incomingAlpha);

  if (nextAlpha <= 0) {
    return;
  }

  const applyChannel = (channelIndex) => {
    const existing = buffer[offset + channelIndex] / 255;
    const incoming = color[channelIndex] / 255;
    const next =
      (incoming * incomingAlpha + existing * existingAlpha * (1 - incomingAlpha)) / nextAlpha;
    buffer[offset + channelIndex] = Math.round(next * 255);
  };

  applyChannel(0);
  applyChannel(1);
  applyChannel(2);
  buffer[offset + 3] = Math.round(nextAlpha * 255);
}

function drawRoundedRect(buffer, size, x, y, width, height, radius, color) {
  const right = x + width;
  const bottom = y + height;

  for (let py = y; py < bottom; py += 1) {
    for (let px = x; px < right; px += 1) {
      const dx = px < x + radius ? x + radius - px : px >= right - radius ? px - (right - radius - 1) : 0;
      const dy = py < y + radius ? y + radius - py : py >= bottom - radius ? py - (bottom - radius - 1) : 0;

      if (dx === 0 || dy === 0 || dx * dx + dy * dy <= radius * radius) {
        setPixel(buffer, size, px, py, color);
      }
    }
  }
}

function drawCircle(buffer, size, centerX, centerY, radius, color) {
  const r2 = radius * radius;
  for (let y = Math.floor(centerY - radius); y <= Math.ceil(centerY + radius); y += 1) {
    for (let x = Math.floor(centerX - radius); x <= Math.ceil(centerX + radius); x += 1) {
      const dx = x - centerX;
      const dy = y - centerY;
      if (dx * dx + dy * dy <= r2) {
        blendPixel(buffer, size, x, y, color);
      }
    }
  }
}

function drawArrow(buffer, size, { x, y, width, height, direction, color }) {
  const shaftThickness = Math.max(2, Math.round(height * 0.34));
  const shaftTop = Math.round(y + (height - shaftThickness) / 2);
  const shaftBottom = shaftTop + shaftThickness;
  const headWidth = Math.max(3, Math.round(width * 0.28));
  const shaftStart = direction === 'right' ? x : x + headWidth;
  const shaftEnd = direction === 'right' ? x + width - headWidth : x + width;

  for (let py = shaftTop; py < shaftBottom; py += 1) {
    for (let px = shaftStart; px < shaftEnd; px += 1) {
      blendPixel(buffer, size, px, py, color);
    }
  }

  const tipX = direction === 'right' ? x + width - 1 : x;
  const baseX = direction === 'right' ? x + width - headWidth : x + headWidth;
  const midY = y + height / 2;

  for (let py = Math.floor(y); py < Math.ceil(y + height); py += 1) {
    const distance = Math.abs(py + 0.5 - midY);
    const ratio = 1 - distance / (height / 2);
    if (ratio < 0) {
      continue;
    }

    const span = Math.max(1, Math.round(headWidth * ratio));
    if (direction === 'right') {
      for (let px = baseX; px <= tipX; px += 1) {
        if (px >= tipX - span) {
          blendPixel(buffer, size, px, py, color);
        }
      }
    } else {
      for (let px = tipX; px <= baseX; px += 1) {
        if (px <= tipX + span) {
          blendPixel(buffer, size, px, py, color);
        }
      }
    }
  }
}

function createIcon(size) {
  const buffer = Buffer.alloc(size * size * 4);

  const navy = [36, 48, 82, 255];
  const navyGlow = [86, 108, 176, 255];
  const white = [244, 247, 255, 255];
  const mint = [114, 230, 202, 255];
  const peach = [255, 166, 127, 255];

  drawRoundedRect(buffer, size, 0, 0, size, size, Math.round(size * 0.24), navy);
  drawCircle(buffer, size, size * 0.22, size * 0.24, size * 0.2, navyGlow);
  drawCircle(buffer, size, size * 0.8, size * 0.78, size * 0.16, peach);

  drawArrow(buffer, size, {
    x: Math.round(size * 0.18),
    y: Math.round(size * 0.28),
    width: Math.round(size * 0.58),
    height: Math.round(size * 0.18),
    direction: 'right',
    color: white
  });

  drawArrow(buffer, size, {
    x: Math.round(size * 0.24),
    y: Math.round(size * 0.54),
    width: Math.round(size * 0.58),
    height: Math.round(size * 0.18),
    direction: 'left',
    color: mint
  });

  return pngFromRgba(size, buffer);
}

export async function generateIcons({
  outputDir = resolve('assets/icons')
} = {}) {
  await mkdir(outputDir, { recursive: true });

  await Promise.all(
    SIZES.map(async (size) => {
      await writeFile(join(outputDir, `icon${size}.png`), createIcon(size));
    })
  );

  return outputDir;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const outputDir = await generateIcons();
  console.log(`Icons generated at ${outputDir}`);
}
