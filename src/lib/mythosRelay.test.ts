import { describe, expect, it } from "vitest";
import { b64u, open, relayKeys, seal } from "./mythosRelay";

describe("Handy-Fernsteuerung (verschlüsselt über ntfy)", () => {
  it("verschlüsselt und entschlüsselt, Themen sind stabil und geheim", async () => {
    const key = b64u(crypto.getRandomValues(new Uint8Array(32)));
    const a = await relayKeys(key), b = await relayKeys(key);
    expect(a.inbox).toBe(b.inbox);
    expect(a.inbox).toMatch(/^mythos-[0-9a-f]{40}-pc$/);
    expect(a.inbox).not.toContain(key);
    const msg = { t: "task", id: "1", text: "Bau mir eine Todo-App 🚀 mit Umlauten äöü" };
    const sealed = await seal(a.aes, msg);
    expect(sealed.length).toBeLessThan(4000);
    expect(await open(b.aes, sealed)).toEqual(msg);
    const other = await relayKeys(b64u(crypto.getRandomValues(new Uint8Array(32))));
    await expect(open(other.aes, sealed)).rejects.toThrow();
  });
  it("lehnt kaputte Links ab", async () => {
    await expect(relayKeys("abc")).rejects.toThrow();
  });
});
