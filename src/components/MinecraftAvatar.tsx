import { useState } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";

/**
 * 2D Minecraft head avatar via crafatar (free, no key).
 * Falls back to initials when no username.
 */
const MinecraftAvatar = ({
  username,
  fallback,
  size = 32,
  className,
}: {
  username?: string | null;
  fallback?: string | null;
  size?: number;
  className?: string;
}) => {
  const [errored, setErrored] = useState(false);
  const initials = (fallback || username || "?").slice(0, 2).toUpperCase();
  const url = username && !errored
    ? `https://mc-heads.net/avatar/${encodeURIComponent(username)}/${size * 2}`
    : undefined;

  return (
    <Avatar
      className={cn("border border-primary/30 shadow-md shadow-primary/20", className)}
      style={{ width: size, height: size }}
    >
      {url && (
        <AvatarImage
          src={url}
          alt={username || "avatar"}
          onError={() => setErrored(true)}
          className="image-rendering-pixelated"
          style={{ imageRendering: "pixelated" }}
        />
      )}
      <AvatarFallback className="bg-gradient-primary text-primary-foreground text-xs font-semibold">
        {initials}
      </AvatarFallback>
    </Avatar>
  );
};

export default MinecraftAvatar;
