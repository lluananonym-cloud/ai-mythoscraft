import { useEffect, useRef, useState } from "react";
import { SkinViewer, WalkingAnimation } from "skinview3d";
import MinecraftAvatar from "./MinecraftAvatar";

/**
 * 3D rotating Minecraft skin viewer using skinview3d.
 * Falls back to a 2D avatar if WebGL is unavailable or the context is lost
 * (happens often in PWA standalone mode on iOS after backgrounding).
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
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!canvasRef.current || !username || failed) return;

    let cancelled = false;
    let viewer: SkinViewer | null = null;

    try {
      viewer = new SkinViewer({
        canvas: canvasRef.current,
        width,
        height,
        preserveDrawingBuffer: true,
      });
      viewer.background = null;
      viewer.animation = new WalkingAnimation();
      viewer.animation.speed = 0.6;
      viewer.controls.enableZoom = false;
      viewer.controls.enablePan = false;
      viewer.zoom = 0.85;
      viewerRef.current = viewer;

      const onLost = (e: Event) => {
        e.preventDefault();
        setFailed(true);
      };
      canvasRef.current.addEventListener("webglcontextlost", onLost, false);

      void viewer
        .loadSkin(`https://mc-heads.net/skin/${encodeURIComponent(username)}`)
        .catch(() => {
          if (cancelled) return;
          setFailed(true);
        });
    } catch {
      setFailed(true);
    }

    return () => {
      cancelled = true;
      try {
        viewer?.dispose();
      } catch {
        /* ignore */
      }
      viewerRef.current = null;
    };
  }, [username, width, height, failed]);

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

  if (failed) {
    return (
      <div
        className="glass-strong rounded-2xl p-3 flex flex-col items-center justify-center gap-2"
        style={{ width, height }}
      >
        <MinecraftAvatar username={username} fallback={username} size={Math.min(width, height) - 40} />
        <div className="text-[10px] text-muted-foreground">2D Fallback</div>
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
