const MythosBackground = () => (
  <div className="fixed inset-0 -z-10 overflow-hidden pointer-events-none">
    {/* Soft milky blobs */}
    <div className="blob h-[600px] w-[600px] -left-40 -top-40 bg-white/30" />
    <div className="blob h-[700px] w-[700px] right-[-250px] top-[15%] bg-white/15" style={{ animationDelay: '6s' }} />
    <div className="blob h-[500px] w-[500px] left-[25%] bottom-[-150px] bg-white/20" style={{ animationDelay: '12s' }} />

    {/* Subtle grain */}
    <div
      className="absolute inset-0 opacity-[0.04] mix-blend-overlay"
      style={{
        backgroundImage:
          "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/></filter><rect width='100%25' height='100%25' filter='url(%23n)'/></svg>\")",
      }}
    />

    {/* Vignette towards background */}
    <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_-20%,transparent_30%,hsl(var(--background))_85%)]" />
  </div>
);
export default MythosBackground;
