import { useEffect, useState } from "react";
import { LogoMark } from "./Logo";

/**
 * Splash + privacy overlay:
 * - Shows a black screen with the Mythos logo briefly on app open.
 * - Re-shows when the tab is hidden (iOS app switcher / multitasking),
 *   so previews show only the logo on black, not user content.
 */
const SplashScreen = () => {
  const [visible, setVisible] = useState(true);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setVisible(false), 1100);
    const onVis = () => {
      const isHidden = document.visibilityState === "hidden";
      setHidden(isHidden);
      if (!isHidden) {
        setVisible(true);
        window.setTimeout(() => setVisible(false), 360);
      }
    };
    document.addEventListener("visibilitychange", onVis);
    return () => {
      clearTimeout(t);
      document.removeEventListener("visibilitychange", onVis);
    };
  }, []);

  if (!visible && !hidden) return null;

  return (
    <div
      aria-hidden
      className="fixed inset-0 z-[9999] flex items-center justify-center bg-black animate-in fade-in"
      style={{ transition: "opacity 400ms" }}
    >
      <LogoMark size="lg" className="h-28 w-28 animate-pulse drop-shadow-[0_0_34px_hsl(0_0%_100%_/_0.22)]" />
    </div>
  );
};

export default SplashScreen;
