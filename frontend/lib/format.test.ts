import { describe, expect, it } from "vitest";
import { formatBytes, formatDateTime } from "@/lib/format";

describe("formatBytes", () => {
  it("formats bytes below 1 KB", () => {
    expect(formatBytes(512)).toBe("512 B");
  });

  it("formats megabytes like template limits", () => {
    expect(formatBytes(5 * 1024 * 1024)).toBe("5 MB");
    expect(formatBytes(10 * 1024 * 1024)).toBe("10 MB");
  });

  it("falls back for invalid input", () => {
    expect(formatBytes(Number.NaN)).toBe("未知");
    expect(formatBytes(-1)).toBe("未知");
  });
});

describe("formatDateTime", () => {
  it("returns a placeholder for empty values", () => {
    expect(formatDateTime(null)).toBe("—");
    expect(formatDateTime("not-a-date")).toBe("—");
  });
});