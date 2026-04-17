import { Sparkles } from "lucide-react";

const Logo = ({ size = "md" }: { size?: "sm" | "md" | "lg" }) => {
  const sizes = { sm: "h-8 w-8 text-base", md: "h-10 w-10 text-lg", lg: "h-14 w-14 text-2xl" };
  const text = { sm: "text-lg", md: "text-xl", lg: "text-3xl" };
  return (
    <div className="flex items-center gap-2.5">
      <div className={`${sizes[size]} rounded-xl bg-gradient-primary flex items-center justify-center glow-primary shrink-0`}>
        <Sparkles className="h-1/2 w-1/2 text-primary-foreground" strokeWidth={2.5} />
      </div>
      <span className={`font-display font-bold tracking-tight ${text[size]}`}>
        <span className="gradient-text">Mythos</span>
        <span className="text-foreground"> AI</span>
      </span>
    </div>
  );
};
export default Logo;
