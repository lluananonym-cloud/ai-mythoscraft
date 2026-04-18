import { useEffect, useRef } from "react";
import { SkinViewer, WalkingAnimation } from "skinview3d";

/**
 * 3D rotating Minecraft skin viewer using skinview3d.
 * Loads skin via mc-heads.net (no API key, free).
 */
const MinecraftSkin3D = ({
  username,
  width = 240,
  height = 320,
}: {
  username: string;
  width?: number;
  height?: number;
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const viewerRef = useRef<SkinViewer | null>(null);

  useEffect(() => {
    if (!canvasRef.current || !username) return;

    const viewer = new SkinViewer({
      canvas: canvasRef.current,
      width,
      height,
      skin: `https://mc-heads.net/skin/${encodeURIComponent(username)}`,
    });
    viewer.animation = new WalkingAnimation();
    viewer.animation.speed = 0.6;
    viewer.controls.enableZoom = false;
    viewer.controls.enablePan = false;
    viewer.zoom = 0.85;
    viewerRef.current = viewer;

    return () => {
      viewer.dispose();
      viewerRef.current = null;
    };
  }, [username, width, height]);

  if (!username) {
    return (
      <div
        className="glass rounded-2xl flex items-center justify-center text-xs text-muted-foreground"
        style={{ width, height }}
      >
        Kein Minecraft-Name gesetzt
      </div>
    );
  }

  return (
    <div className="glass-strong rounded-2xl p-3 inline-flex items-center justify-center">
      <canvas ref={canvasRef} style={{ width, height }} />
    </div>
  );
};

export default MinecraftSkin3D;
