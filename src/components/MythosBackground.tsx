const MythosBackground = () => (
  <div className="fixed inset-0 -z-10 overflow-hidden">
    <div className="blob h-[500px] w-[500px] -left-32 -top-32 bg-primary/40" />
    <div className="blob h-[600px] w-[600px] right-[-200px] top-[20%] bg-accent/30" style={{ animationDelay: '5s' }} />
    <div className="blob h-[450px] w-[450px] left-[30%] bottom-[-100px] bg-primary-glow/30" style={{ animationDelay: '10s' }} />
    <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_-20%,transparent_30%,hsl(var(--background))_80%)]" />
  </div>
);
export default MythosBackground;
