// 0% Zoom: iOS Safari ignoriert `user-scalable=no`, daher hier hart unterdrücken.
// - Pinch-Zoom (gesture* Events + Multi-Touch)
// - Double-Tap-Zoom
// - Ctrl/Cmd + Wheel / +/- Tastatur-Zoom (Desktop-PWA)
export function installNoZoom() {
  if (typeof window === "undefined") return;
  if ((window as any).__mythosNoZoom) return;
  (window as any).__mythosNoZoom = true;

  const stop = (e: Event) => e.preventDefault();

  // iOS pinch
  document.addEventListener("gesturestart", stop, { passive: false });
  document.addEventListener("gesturechange", stop, { passive: false });
  document.addEventListener("gestureend", stop, { passive: false });

  // Multi-Touch pinch (auch Android/Chrome)
  document.addEventListener(
    "touchmove",
    (e: TouchEvent) => {
      if (e.touches.length > 1) e.preventDefault();
    },
    { passive: false },
  );
  document.addEventListener(
    "touchstart",
    (e: TouchEvent) => {
      if (e.touches.length > 1) e.preventDefault();
    },
    { passive: false },
  );

  // Double-Tap-Zoom
  let lastTouch = 0;
  document.addEventListener(
    "touchend",
    (e: TouchEvent) => {
      const now = Date.now();
      if (now - lastTouch <= 320) e.preventDefault();
      lastTouch = now;
    },
    { passive: false },
  );

  // Desktop / PWA
  window.addEventListener(
    "wheel",
    (e: WheelEvent) => {
      if (e.ctrlKey || e.metaKey) e.preventDefault();
    },
    { passive: false },
  );
  window.addEventListener("keydown", (e: KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && ["+", "-", "=", "_", "0"].includes(e.key)) e.preventDefault();
  });
}
