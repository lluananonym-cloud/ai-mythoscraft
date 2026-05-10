import { cn } from "@/lib/utils";

type LogoSize = "sm" | "md" | "lg";

const markSizes: Record<LogoSize, string> = {
  sm: "h-9 w-9",
  md: "h-11 w-11",
  lg: "h-16 w-16",
};

const textSizes: Record<LogoSize, string> = {
  sm: "text-lg",
  md: "text-xl",
  lg: "text-3xl",
};

export const LogoMark = ({ size = "md", className }: { size?: LogoSize; className?: string }) => (
  <img
    src="/icon.png"
    alt=""
    aria-hidden="true"
    width={64}
    height={64}
    className={cn("shrink-0 object-contain select-none", markSizes[size], className)}
  />
);

const Logo = ({ size = "md" }: { size?: LogoSize }) => {
  return (
    <div className="flex items-center gap-2.5">
      <LogoMark size={size} />
      <span className={`font-display font-bold tracking-tight ${textSizes[size]}`}>
        <span className="gradient-text">Mythos</span>
        <span className="text-foreground"> AI</span>
      </span>
    </div>
  );
};
export default Logo;
