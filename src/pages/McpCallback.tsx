import { useEffect, useState } from "react";
import { reportCallback } from "@/lib/mcpOauth";

/** Rückleitung nach der Connector-Anmeldung (läuft im Popup): Code ans Hauptfenster geben und schließen. */
export default function McpCallback() {
  const [ok, setOk] = useState<boolean | null>(null);
  useEffect(() => {
    const good = reportCallback();
    setOk(good);
    if (good) setTimeout(() => window.close(), 600);
  }, []);
  return (
    <div className="min-h-screen grid place-items-center p-6 text-center">
      <h2 className="text-xl font-medium">
        {ok === null ? "Verbinde …" : ok ? "✓ Verbunden – du kannst dieses Fenster schließen." : "Anmeldung fehlgeschlagen."}
      </h2>
    </div>
  );
}
