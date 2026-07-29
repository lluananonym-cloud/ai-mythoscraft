import { useEffect, useState, useCallback } from "react";

export function useDesktopNotifications() {
  const [permission, setPermission] = useState<NotificationPermission>(
    typeof Notification !== "undefined" ? Notification.permission : "default"
  );

  const supported = typeof window !== "undefined" && "Notification" in window;

  const request = useCallback(async () => {
    if (!supported) return "denied" as NotificationPermission;
    const p = await Notification.requestPermission();
    setPermission(p);
    return p;
  }, [supported]);

  useEffect(() => {
    if (supported && Notification.permission === "default") {
      // do not auto-request — show a button in UI
    }
  }, [supported]);

  const notify = useCallback(
    (title: string, opts?: NotificationOptions & { onClick?: () => void }) => {
      if (!supported || Notification.permission !== "granted") return;
      try {
        const n = new Notification(title, { icon: "/favicon.ico", ...opts });
        if (opts?.onClick) n.onclick = () => { window.focus(); opts.onClick!(); n.close(); };
      } catch (e) {
        console.warn("notification error", e);
      }
    },
    [supported]
  );

  return { supported, permission, request, notify };
}
