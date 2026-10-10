// Mythos Client (Minecraft) Backend: Plus, Währung, /mcc und Creator-Bewerbungen.
//
// Anmeldung ohne Passwort über Mojang: Die Mod ruft joinServer(serverId) bei Mojang auf und schickt
// dann { action: "session", username, serverId }. Diese Funktion fragt Mojang (hasJoined), ob genau
// dieser Account sich gerade so angemeldet hat, und gibt ein Sitzungs-Token zurück (30 Tage).
//
// Aktionen (POST JSON { action, ... }; Token als "Authorization: Bearer <token>"):
//   session { username, serverId }        -> { token, profile }
//   profile                               -> { profile }                    (Token)
//   give { amount }                       -> { profile }                    (Token, nur Owner: /mcc <anzahl>)
//   players { uuids: string[] }           -> { players: [{ uuid, name, plus }] }  (öffentlich, für Plus-Abzeichen)
//   creator-apply { mcName, discord, platform, channelUrl, followers, message } -> { ok }
//   cosmetics                             -> { cosmetics, equippedCape }    (öffentlich, Token optional)
//   cosmetic-create { name, imageBase64, frames, frameTimeMs, plusOnly } -> { cosmetic }  (Token, nur Owner)
//   cosmetic-delete { id }                -> { ok }                          (Token, nur Owner)
//   equip { cosmeticId | null }           -> { profile }                    (Token)
//
// Capes: PNG, Breite ein Vielfaches von 64, pro Bild Breite:Höhe = 2:1. Animierte Capes sind
// mehrere Bilder senkrecht untereinander (frames), die alle frameTimeMs weitergeschaltet werden.
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

const admin = () => createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const SESSION_DAYS = 30;
const BUCKET = "mcc-cosmetics";
const MAX_TEXTURE_BYTES = 2 * 1024 * 1024;
const MAX_GIVE = 1_000_000_000;

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomToken(): string {
  const b = new Uint8Array(32);
  crypto.getRandomValues(b);
  return "mcc_" + Array.from(b).map((x) => x.toString(16).padStart(2, "0")).join("");
}

const cleanUuid = (u: string) => u.replace(/-/g, "").toLowerCase();
const str = (v: unknown, max: number) => (typeof v === "string" ? v.trim().slice(0, max) : "");

type Player = {
  uuid: string; name: string; plus: boolean; coins: number; is_owner: boolean; equipped_cape: string | null;
};
const publicProfile = (p: Player) => ({
  uuid: p.uuid, name: p.name, plus: p.plus, coins: p.coins, owner: p.is_owner, equippedCape: p.equipped_cape,
});

type Cosmetic = {
  id: string; name: string; type: string; texture_path: string; frames: number; frame_time_ms: number;
  plus_only: boolean;
};
const textureUrl = (path: string) => `${Deno.env.get("SUPABASE_URL")}/storage/v1/object/public/${BUCKET}/${path}`;
const publicCosmetic = (c: Cosmetic) => ({
  id: c.id, name: c.name, type: c.type, url: textureUrl(c.texture_path), frames: c.frames,
  frameTimeMs: c.frame_time_ms, plusOnly: c.plus_only,
});
const COSMETIC_COLUMNS = "id, name, type, texture_path, frames, frame_time_ms, plus_only";

/** Liest Breite und Höhe aus dem PNG-Kopf (IHDR), ohne das Bild zu dekodieren. */
function pngSize(bytes: Uint8Array): { width: number; height: number } | null {
  const sig = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  if (bytes.length < 24 || sig.some((b, i) => bytes[i] !== b)) return null;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  return { width: view.getUint32(16), height: view.getUint32(20) };
}

function decodeBase64(data: string): Uint8Array | null {
  try {
    const bin = atob(data.replace(/^data:image\/png;base64,/, ""));
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  } catch {
    return null;
  }
}

async function config(db: ReturnType<typeof admin>, key: string): Promise<string> {
  const { data } = await db.from("mcc_config").select("value").eq("key", key).maybeSingle();
  return (data as { value?: string } | null)?.value ?? "";
}

async function playerFromToken(db: ReturnType<typeof admin>, req: Request): Promise<Player | null> {
  const m = (req.headers.get("authorization") ?? "").match(/^Bearer\s+(mcc_[0-9a-f]{64})$/i);
  if (!m) return null;
  const { data: s } = await db.from("mcc_sessions").select("player_uuid, expires_at")
    .eq("token_hash", await sha256(m[1])).maybeSingle();
  if (!s || new Date(s.expires_at).getTime() < Date.now()) return null;
  const { data: p } = await db.from("mcc_players").select("*").eq("uuid", s.player_uuid).maybeSingle();
  return (p as Player | null) ?? null;
}

async function session(db: ReturnType<typeof admin>, body: Record<string, unknown>) {
  const username = str(body.username, 16);
  const serverId = str(body.serverId, 64);
  if (!/^[A-Za-z0-9_]{3,16}$/.test(username) || !/^[A-Za-z0-9-]{8,64}$/.test(serverId)) {
    return json({ error: "Ungültiger Name oder serverId" }, 400);
  }
  const r = await fetch(
    `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`,
  );
  if (r.status !== 200) return json({ error: "Minecraft-Anmeldung nicht bestätigt" }, 401);
  const mojang = await r.json() as { id: string; name: string };
  const uuid = cleanUuid(mojang.id);

  const { data: existing } = await db.from("mcc_players").select("*").eq("uuid", uuid).maybeSingle();
  let player = existing as Player | null;
  if (!player) {
    const ins = await db.from("mcc_players").insert({ uuid, name: mojang.name }).select("*").single();
    if (ins.error) throw ins.error;
    player = ins.data as Player;
  } else if (player.name !== mojang.name) {
    await db.from("mcc_players").update({ name: mojang.name, updated_at: new Date().toISOString() }).eq("uuid", uuid);
    player.name = mojang.name;
  }

  // Owner beim ersten bestätigten Login festlegen: Plus und Startwährung.
  if (!player.is_owner && mojang.name.toLowerCase() === (await config(db, "owner_name")).toLowerCase()) {
    const { data: owner } = await db.from("mcc_players").select("uuid").eq("is_owner", true).maybeSingle();
    if (!owner) {
      const start = Number(await config(db, "owner_start_coins")) || 0;
      await db.from("mcc_players").update({ is_owner: true, plus: true, updated_at: new Date().toISOString() }).eq("uuid", uuid);
      const coins = start > 0 ? await db.rpc("mcc_add_coins", { _uuid: uuid, _amount: start, _reason: "owner-start" }) : null;
      player = { ...player, is_owner: true, plus: true, coins: (coins?.data as number | undefined) ?? player.coins };
    }
  }

  const token = randomToken();
  const expires = new Date(Date.now() + SESSION_DAYS * 86400_000).toISOString();
  const s = await db.from("mcc_sessions").insert({ token_hash: await sha256(token), player_uuid: uuid, expires_at: expires });
  if (s.error) throw s.error;
  await db.from("mcc_sessions").delete().lt("expires_at", new Date().toISOString());
  return json({ token, expiresAt: expires, profile: publicProfile(player) });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Nur POST" }, 405);
  try {
    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const db = admin();
    switch (body.action) {
      case "session":
        return await session(db, body);

      case "profile": {
        const p = await playerFromToken(db, req);
        if (!p) return json({ error: "Nicht angemeldet" }, 401);
        return json({ profile: publicProfile(p) });
      }

      case "give": {
        const p = await playerFromToken(db, req);
        if (!p) return json({ error: "Nicht angemeldet" }, 401);
        if (!p.is_owner) return json({ error: "Nur der Owner darf /mcc benutzen" }, 403);
        const amount = Number(body.amount);
        if (!Number.isInteger(amount) || amount < 1 || amount > MAX_GIVE) {
          return json({ error: `Anzahl muss zwischen 1 und ${MAX_GIVE} liegen` }, 400);
        }
        const { data, error } = await db.rpc("mcc_add_coins", { _uuid: p.uuid, _amount: amount, _reason: "owner-mcc" });
        if (error) throw error;
        return json({ profile: publicProfile({ ...p, coins: data as number }) });
      }

      case "players": {
        const uuids = Array.isArray(body.uuids)
          ? body.uuids.filter((u): u is string => typeof u === "string").slice(0, 200).map(cleanUuid)
          : [];
        if (!uuids.length) return json({ players: [] });
        const { data } = await db.from("mcc_players")
          .select(`uuid, name, plus, cape:mcc_cosmetics(${COSMETIC_COLUMNS}, active)`).in("uuid", uuids);
        const players = (data ?? []).map((p) => {
          const cape = p.cape as unknown as (Cosmetic & { active: boolean }) | null;
          return { uuid: p.uuid, name: p.name, plus: p.plus, cape: cape?.active ? publicCosmetic(cape) : null };
        });
        return json({ players });
      }

      case "cosmetics": {
        const { data, error } = await db.from("mcc_cosmetics").select(COSMETIC_COLUMNS)
          .eq("active", true).order("created_at", { ascending: false });
        if (error) throw error;
        const p = await playerFromToken(db, req);
        return json({
          cosmetics: (data as Cosmetic[]).map(publicCosmetic),
          equippedCape: p?.equipped_cape ?? null,
          profile: p ? publicProfile(p) : null,
        });
      }

      case "cosmetic-create": {
        const p = await playerFromToken(db, req);
        if (!p) return json({ error: "Nicht angemeldet" }, 401);
        if (!p.is_owner) return json({ error: "Nur der Owner darf Cosmetics erstellen" }, 403);
        const name = str(body.name, 40);
        const frames = Math.floor(Number(body.frames) || 1);
        const frameTimeMs = Math.floor(Number(body.frameTimeMs) || 100);
        if (!name) return json({ error: "Name fehlt" }, 400);
        if (frames < 1 || frames > 64) return json({ error: "Bilder: 1 bis 64" }, 400);
        if (frameTimeMs < 20 || frameTimeMs > 5000) return json({ error: "Bildzeit: 20 bis 5000 ms" }, 400);
        const bytes = typeof body.imageBase64 === "string" ? decodeBase64(body.imageBase64) : null;
        if (!bytes || bytes.length > MAX_TEXTURE_BYTES) return json({ error: "Bitte ein PNG bis 2 MB hochladen" }, 400);
        const size = pngSize(bytes);
        if (!size) return json({ error: "Die Datei ist kein PNG" }, 400);
        if (size.width < 64 || size.width % 64 !== 0 || size.height !== frames * (size.width / 2)) {
          return json({
            error: `Falsche Größe: ${size.width}x${size.height}. Ein Bild muss 64x32 (oder ein Vielfaches) sein, ` +
              `bei ${frames} Bildern also ${size.width}x${frames * (size.width / 2)}.`,
          }, 400);
        }
        const id = crypto.randomUUID();
        const path = `capes/${id}.png`;
        const up = await db.storage.from(BUCKET).upload(path, bytes, { contentType: "image/png", upsert: false });
        if (up.error) throw up.error;
        const ins = await db.from("mcc_cosmetics").insert({
          id, name, type: "cape", texture_path: path, frames, frame_time_ms: frameTimeMs,
          plus_only: body.plusOnly === true, created_by: p.uuid,
        }).select(COSMETIC_COLUMNS).single();
        if (ins.error) throw ins.error;
        return json({ cosmetic: publicCosmetic(ins.data as Cosmetic) });
      }

      case "cosmetic-delete": {
        const p = await playerFromToken(db, req);
        if (!p) return json({ error: "Nicht angemeldet" }, 401);
        if (!p.is_owner) return json({ error: "Nur der Owner darf Cosmetics löschen" }, 403);
        const id = str(body.id, 64);
        const { error } = await db.from("mcc_cosmetics").update({ active: false }).eq("id", id);
        if (error) throw error;
        return json({ ok: true });
      }

      case "equip": {
        const p = await playerFromToken(db, req);
        if (!p) return json({ error: "Nicht angemeldet" }, 401);
        const id = body.cosmeticId == null ? null : str(body.cosmeticId, 64);
        if (id) {
          const { data: c } = await db.from("mcc_cosmetics").select("active, plus_only").eq("id", id).maybeSingle();
          if (!c?.active) return json({ error: "Dieses Cosmetic gibt es nicht" }, 404);
          if (c.plus_only && !p.plus) return json({ error: "Dieses Cosmetic gibt es nur mit Plus" }, 403);
        }
        const { error } = await db.from("mcc_players")
          .update({ equipped_cape: id, updated_at: new Date().toISOString() }).eq("uuid", p.uuid);
        if (error) throw error;
        return json({ profile: publicProfile({ ...p, equipped_cape: id }) });
      }

      case "creator-apply": {
        const mcName = str(body.mcName, 16);
        const discord = str(body.discord, 64);
        const platform = str(body.platform, 32);
        const channelUrl = str(body.channelUrl, 300);
        const followers = Math.max(0, Math.min(2_000_000_000, Math.floor(Number(body.followers) || 0)));
        const message = str(body.message, 2000);
        if (!/^[A-Za-z0-9_]{3,16}$/.test(mcName)) return json({ error: "Bitte gültigen Minecraft-Namen angeben" }, 400);
        if (!discord || !platform) return json({ error: "Discord und Plattform fehlen" }, 400);
        if (!/^https?:\/\/\S+$/i.test(channelUrl)) return json({ error: "Bitte gültigen Kanal-Link angeben" }, 400);
        const p = await playerFromToken(db, req);
        const ins = await db.from("mcc_creator_applications").insert({
          mc_name: mcName, mc_uuid: p?.uuid ?? null, discord, platform, channel_url: channelUrl, followers, message,
        });
        if (ins.error?.code === "23505") return json({ error: "Für diesen Namen läuft schon eine Bewerbung" }, 409);
        if (ins.error) throw ins.error;
        return json({ ok: true });
      }

      default:
        return json({ error: "Unbekannte Aktion" }, 400);
    }
  } catch (e) {
    console.error("mcc error", e);
    return json({ error: "Serverfehler" }, 500);
  }
});
