import { useEffect, useState } from "react";
import { finishRedirect } from "@/lib/mcpOAuth";

/** Rückleitung nach der Anmeldung bei einem MCP-Connector: gibt den Code an Mythos weiter und schließt sich. */
export default function McpOAuth() {
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    const p = new URLSearchParams(location.search);
    finishRedirect(p);
    if (p.get("error")) setError(p.get("error_description") || p.get("error"));
    else setTimeout(() => window.close(), 300);
  }, []);
  return (
    <div className="min-h-screen flex items-center justify-center p-6 text-center">
      <div className="space-y-2">
        <div className="text-lg font-medium">{error ? "Anmeldung fehlgeschlagen" : "Verbunden"}</div>
        <p className="text-sm text-muted-foreground">{error || "Du kannst dieses Fenster schließen und zu Mythos zurückkehren."}</p>
      </div>
    </div>
  );
}
