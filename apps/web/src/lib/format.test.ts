import { describe, expect, it } from "vitest";

import { formatRelativeTime } from "./format";

describe("formatRelativeTime", () => {
  it("returns the input verbatim when it is not a valid date", () => {
    expect(formatRelativeTime("not-a-date")).toBe("not-a-date");
    expect(formatRelativeTime("")).toBe("");
  });

  it("formats a recent past date as an 'ago' phrase", () => {
    const iso = new Date(Date.now() - 5_000).toISOString(); // ~5s ago
    expect(formatRelativeTime(iso)).toMatch(/ago$/);
  });

  it("formats a clearly future date without 'ago'", () => {
    const iso = new Date(Date.now() + 60 * 60_000).toISOString(); // ~1h ahead
    expect(formatRelativeTime(iso)).not.toMatch(/ago$/);
  });

  it("picks the day unit for a date a couple of days in the past", () => {
    const iso = new Date(Date.now() - 3 * 86_400_000).toISOString(); // ~3d ago
    expect(formatRelativeTime(iso)).toMatch(/day/);
  });
});
