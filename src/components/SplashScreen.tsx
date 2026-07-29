import { useEffect, useLayoutEffect, useState } from "react";
import { LogoMark } from "./Logo";

/**
 * Splash + privacy overlay.
 *
 * - Shows a black screen with the Mythos logo briefly on app open.
 * - Re-shows immediately when the app loses focus or is moved to the
 *   background, so the iOS app-switcher snapshot never reveals user data.
 *
 * iOS Safari does NOT fire `visibilitychange` reliably when entering the
 * app switcher, so we listen to a wider set of events (`pagehide`, `blur`,
 * `freeze`) and use `useLayoutEffect` so the overlay is mounted synchronously
 * before the next paint that the OS snapshots.
 */
const SplashScreen = () => {
  const [visible, setVisible] = useState(true);
  const [hidden, setHidden] = useState(false);

  // Initial splash on cold start.
  useEffect(() => {
    const t = setTimeout(() => setVisible(false), 900);
    return () => clearTimeout(t);
  }, []);

  // Privacy overlay — mounted synchronously via useLayoutEffect.
  useLayoutEffect(() => {
    const hide = () => setHidden(true);
    const show = () => {
      setHidden(false);
      // brief logo flash on resume so it feels intentional, not laggy
      setVisible(true);
      window.setTimeout(() => setVisible(false), 320);
    };
    const onVis = () => {
      if (document.visibilityState === "hidden") hide();
      else show();
    };

    document.addEventListener("visibilitychange", onVis);
    window.addEventListener("pagehide", hide);
    window.addEventListener("pageshow", show);
    window.addEventListener("blur", hide);
    window.addEventListener("focus", show);
    // Chromium freeze/resume
    document.addEventListener("freeze", hide as any);
    document.addEventListener("resume", show as any);

    return () => {
      document.removeEventListener("visibilitychange", onVis);
      window.removeEventListener("pagehide", hide);
      window.removeEventListener("pageshow", show);
      window.removeEventListener("blur", hide);
      window.removeEventListener("focus", show);
      document.removeEventListener("freeze", hide as any);
      document.removeEventListener("resume", show as any);
    };
  }, []);

  if (!visible && !hidden) return null;

  return (
    <div
      aria-hidden
      className="fixed inset-0 z-[9999] flex items-center justify-center"
      style={{
        background: "#000",
        // ensure overlay fills the whole screen including notch/home-bar areas
        paddingTop: "env(safe-area-inset-top)",
        paddingBottom: "env(safe-area-inset-bottom)",
      }}
    >
      <LogoMark
        size="lg"
        className="h-32 w-32 animate-pulse drop-shadow-[0_0_42px_hsl(0_0%_100%_/_0.28)]"
      />
    </div>
  );
};

export default SplashScreen;
