/**
 * Persistent session backup for PWA / iOS Safari.
 *
 * Problem: iOS Safari (and standalone PWAs) wipe localStorage after ~7 days
 * of inactivity due to ITP. Result: users get logged out of installed PWAs.
 *
 * Solution: When the user enables "Stay logged in" in account settings,
 * we mirror the Supabase session (access + refresh token) into IndexedDB,
 * which survives ITP. On app boot, if there is no active session, we restore
 * from the IDB backup.
 */
import { supabase } from "@/integrations/supabase/client";

const FLAG_KEY = "mythos_persistent_session";
const DB_NAME = "mythos-auth";
const STORE = "session";
const KEY = "current";

export const isPersistentSessionEnabled = () =>
  typeof localStorage !== "undefined" && localStorage.getItem(FLAG_KEY) === "1";

export const setPersistentSessionEnabled = async (enabled: boolean) => {
  localStorage.setItem(FLAG_KEY, enabled ? "1" : "0");
  if (!enabled) {
    await idbDelete();
  } else {
    const { data } = await supabase.auth.getSession();
    if (data.session) await idbPut(data.session);
  }
};

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function idbPut(session: { access_token: string; refresh_token: string }) {
  try {
    const db = await openDB();
    await new Promise<void>((res, rej) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).put(
        { access_token: session.access_token, refresh_token: session.refresh_token, saved_at: Date.now() },
        KEY,
      );
      tx.oncomplete = () => res();
      tx.onerror = () => rej(tx.error);
    });
  } catch {/* ignore */}
}

async function idbGet(): Promise<{ access_token: string; refresh_token: string } | null> {
  try {
    const db = await openDB();
    return await new Promise((res, rej) => {
      const tx = db.transaction(STORE, "readonly");
      const req = tx.objectStore(STORE).get(KEY);
      req.onsuccess = () => res(req.result || null);
      req.onerror = () => rej(req.error);
    });
  } catch {
    return null;
  }
}

async function idbDelete() {
  try {
    const db = await openDB();
    await new Promise<void>((res) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).delete(KEY);
      tx.oncomplete = () => res();
    });
  } catch {/* ignore */}
}

/**
 * Initialize: try to restore from IDB if no active session, then keep IDB
 * in sync with future auth state changes (only while flag is enabled).
 */
export async function initPersistentSession() {
  if (typeof window === "undefined" || !("indexedDB" in window)) return;

  // Restore on boot if user opted in.
  if (isPersistentSessionEnabled()) {
    try {
      const { data } = await supabase.auth.getSession();
      if (!data.session) {
        const backup = await idbGet();
        if (backup?.refresh_token) {
          await supabase.auth.setSession({
            access_token: backup.access_token,
            refresh_token: backup.refresh_token,
          });
        }
      }
    } catch {/* ignore */}
  }

  // Mirror future sessions to IDB.
  supabase.auth.onAuthStateChange((event, session) => {
    if (!isPersistentSessionEnabled()) return;
    if (event === "SIGNED_OUT") {
      void idbDelete();
    } else if (session) {
      void idbPut(session);
    }
  });
}
