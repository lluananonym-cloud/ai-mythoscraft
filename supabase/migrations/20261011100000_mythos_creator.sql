-- Mythos Client: Creator-Status. Der Owner ist automatisch Creator; eine angenommene Bewerbung macht den Spieler zum Creator.

ALTER TABLE public.mcc_players
  ADD COLUMN IF NOT EXISTS creator boolean NOT NULL DEFAULT false;

UPDATE public.mcc_players SET creator = true WHERE is_owner;

CREATE OR REPLACE FUNCTION public.mcc_application_accepted()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NEW.status = 'accepted' AND OLD.status IS DISTINCT FROM 'accepted' THEN
    UPDATE public.mcc_players
       SET creator = true, updated_at = now()
     WHERE uuid = NEW.mc_uuid OR lower(name) = lower(NEW.mc_name);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS mcc_application_accepted ON public.mcc_creator_applications;
CREATE TRIGGER mcc_application_accepted
  AFTER UPDATE OF status ON public.mcc_creator_applications
  FOR EACH ROW EXECUTE FUNCTION public.mcc_application_accepted();
