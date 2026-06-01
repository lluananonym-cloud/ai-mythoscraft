import { createContext, useContext, useEffect, useState, ReactNode, useCallback } from "react";
import { Session, User } from "@supabase/supabase-js";
import { supabase } from "@/integrations/supabase/client";

interface Profile {
  display_name: string | null;
  email: string | null;
  mc_username: string | null;
  start_in_chat: boolean;
}

interface AuthCtx {
  user: User | null;
  session: Session | null;
  loading: boolean;
  isAdmin: boolean;
  profile: Profile | null;
  refreshProfile: () => Promise<void>;
  signOut: () => Promise<void>;
}
const Ctx = createContext<AuthCtx>({} as AuthCtx);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [session, setSession] = useState<Session | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);

  const loadExtras = useCallback(async (uid: string) => {
    const [rolesRes, profRes] = await Promise.all([
      supabase.from("user_roles").select("role").eq("user_id", uid),
      supabase.from("profiles").select("display_name,email,mc_username,start_in_chat").eq("user_id", uid).maybeSingle(),
    ]);
    setIsAdmin(!!rolesRes.data?.some((r: any) => r.role === "admin"));
    setProfile((profRes.data as any) ?? null);
  }, []);

  const refreshProfile = useCallback(async () => {
    if (user) await loadExtras(user.id);
  }, [user, loadExtras]);

  useEffect(() => {
    const { data: sub } = supabase.auth.onAuthStateChange((_e, s) => {
      setSession(s);
      setUser(s?.user ?? null);
      if (s?.user) {
        setTimeout(() => loadExtras(s.user.id), 0);
      } else {
        setIsAdmin(false);
        setProfile(null);
      }
    });
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      setUser(session?.user ?? null);
      setLoading(false);
      if (session?.user) loadExtras(session.user.id);
    });
    return () => sub.subscription.unsubscribe();
  }, [loadExtras]);

  const signOut = async () => { await supabase.auth.signOut(); };

  return (
    <Ctx.Provider value={{ user, session, loading, isAdmin, profile, refreshProfile, signOut }}>
      {children}
    </Ctx.Provider>
  );
};

export const useAuth = () => useContext(Ctx);
