import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import Logo from "@/components/Logo";
import { Home } from "lucide-react";

const NotFound = () => (
  <div className="min-h-screen flex flex-col items-center justify-center px-4 text-center">
    <Logo size="lg" />
    <h1 className="font-display text-7xl font-bold gradient-text mt-8 mb-2">404</h1>
    <p className="text-muted-foreground mb-6">Diese Dimension existiert nicht.</p>
    <Link to="/"><Button className="bg-gradient-primary text-primary-foreground"><Home className="h-4 w-4 mr-1.5" />Zurück zum Hub</Button></Link>
  </div>
);
export default NotFound;
