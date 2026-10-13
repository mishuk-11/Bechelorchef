import { describe, expect, it } from "vitest";
import { currentMonth, dhakaDate, formatBdt, formatQuantity, safeHttpsUrl, safeNext, timeCategory } from "./format";

describe("formatBdt", () => {
  it("formats taka with two decimals", () => {
    expect(formatBdt(80)).toBe("৳80.00");
    expect(formatBdt(1234.5)).toBe("৳1,234.50");
    expect(formatBdt(0)).toBe("৳0.00");
  });
  it("shows a dash for missing values", () => {
    expect(formatBdt(null)).toBe("—");
    expect(formatBdt(undefined)).toBe("—");
  });
});

describe("formatQuantity", () => {
  it("trims trailing zeros and keeps at most 3 decimals", () => {
    expect(formatQuantity(4)).toBe("4");
    expect(formatQuantity(0.75)).toBe("0.75");
    expect(formatQuantity(0.3333333)).toBe("0.333");
  });
});

describe("timeCategory (mirrors the backend thresholds)", () => {
  it.each([
    [1, "EXPRESS"], [15, "EXPRESS"], [16, "BACHELOR_QUICK"], [20, "BACHELOR_QUICK"], [21, "EXTENDED"], [60, "EXTENDED"],
  ])("total %i -> %s", (total, expected) => {
    expect(timeCategory(total)).toBe(expected);
  });
  it.each([0, 61])("rejects %i", (total) => {
    expect(timeCategory(total)).toBeNull();
  });
});

describe("safeHttpsUrl", () => {
  it("allows only https", () => {
    expect(safeHttpsUrl("https://res.cloudinary.com/a.jpg")).toBe("https://res.cloudinary.com/a.jpg");
    expect(safeHttpsUrl("http://example.com/a.jpg")).toBeNull();
    expect(safeHttpsUrl("javascript:alert(1)")).toBeNull();
    expect(safeHttpsUrl("not a url")).toBeNull();
    expect(safeHttpsUrl(null)).toBeNull();
  });
});

describe("safeNext (open-redirect guard)", () => {
  it("keeps same-site paths and rejects others", () => {
    expect(safeNext("/cookbook")).toBe("/cookbook");
    expect(safeNext("//evil.example")).toBe("/");
    expect(safeNext("https://evil.example")).toBe("/");
    expect(safeNext("/" + "\\" + "evil.example")).toBe("/");
    expect(safeNext(null, "/match")).toBe("/match");
  });
});

describe("Dhaka dates", () => {
  it("uses Asia/Dhaka (UTC+6) for month and day boundaries", () => {
    const lateUtc = new Date("2026-09-30T18:30:00Z"); // 2026-10-01 00:30 in Dhaka
    expect(currentMonth(lateUtc)).toBe("2026-10");
    expect(dhakaDate(0, lateUtc)).toBe("2026-10-01");
    expect(dhakaDate(-1, lateUtc)).toBe("2026-09-30");
  });
});
