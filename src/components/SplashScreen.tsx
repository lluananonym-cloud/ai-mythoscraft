import { useEffect, useState } from "react";

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
    const onVis = () => setHidden(document.visibilityState === "hidden");
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
      <img
        src="/icon.png"
        alt=""
        width={160}
        height={160}
        className="w-40 h-40 animate-pulse drop-shadow-[0_0_40px_rgba(139,92,246,0.6)]"
      />
    </div>
  );
};

export default SplashScreen;
