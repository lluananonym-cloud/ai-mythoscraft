import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { LogoMark } from "@/components/Logo";
import { ArrowUp, Loader2 } from "lucide-react";
import { z } from "zod";

const STEPS = [
  { key: "produkt", q: "Hey! Ich bin Mythos AI. Wofür möchtest du Werbung machen? (Name deines Projekts, Servers, Produkts …)", max: 200 },
  { key: "link", q: "Super! Welcher Link soll in der Werbung stehen?", max: 300 },
  { key: "text", q: "Welcher kurze Werbetext soll angezeigt werden?", max: 500 },
  { key: "zeitraum", q: "Wie lange soll die Werbung laufen und hast du ein Budget im Kopf?", max: 300 },
  { key: "kontakt", q: "Zum Schluss: Unter welcher E-Mail-Adresse können wir dich erreichen?", max: 255, email: true },
] as const;

type Msg = { role: "ai" | "user"; text: string };

export default function Werbung() {
  const [msgs, setMsgs] = useState<Msg[]>([{ role: "ai", text: STEPS[0].q }]);
  const [step, setStep] = useState(0);
  const [data, setData] = useState<Record<string, string>>({});
  const [input, setInput] = useState("");
  const [state, setState] = useState<"ask" | "sending" | "done">("ask");
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => { end.current?.scrollIntoView({ behavior: "smooth" }); }, [msgs]);
  useEffect(() => { document.title = "Werbung buchen – Mythos AI"; }, []);

  const submit = async () => {
    const s = STEPS[step]; const v = input.trim();
    if (!v || state !== "ask") return;
    const ok = s.email ? z.string().email().max(255).safeParse(v).success : v.length <= s.max;
    setMsgs((m) => [...m, { role: "user", text: v }]); setInput("");
    if (!ok) { setMsgs((m) => [...m, { role: "ai", text: s.email ? "Das sieht nicht wie eine gültige E-Mail aus – versuch es nochmal." : `Bitte maximal ${s.max} Zeichen.` }]); return; }
    const next = { ...data, [s.key]: v }; setData(next);
    if (step + 1 < STEPS.length) { setStep(step + 1); setMsgs((m) => [...m, { role: "ai", text: STEPS[step + 1].q }]); return; }
    setState("sending");
    try {
      const r = await fetch("https://formsubmit.co/ajax/lluan.anonym@gmail.com", {
        method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ _subject: "Neue Chat-Werbung Anfrage (Mythos AI)", _template: "table", _replyto: next.kontakt, ...next }),
      });
      if (!r.ok) throw new Error();
      setState("done");
      setMsgs((m) => [...m, { role: "ai", text: "Danke! Deine Anfrage ist raus. Wir melden uns per E-Mail bei dir, um deine Chat-Werbung zu buchen. 🚀" }]);
    } catch {
      setState("ask");
      setMsgs((m) => [...m, { role: "ai", text: "Senden hat nicht geklappt. Schick deine E-Mail-Adresse bitte nochmal." }]);
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center px-4 py-8">
      <div className="w-full max-w-xl flex items-center gap-3 mb-6">
        <Link to="/"><LogoMark size="sm" /></Link>
        <div><h1 className="font-display text-xl font-bold">Chat-Werbung buchen</h1><p className="text-xs text-muted-foreground">Mythos AI stellt dir ein paar Fragen</p></div>
      </div>
      <div className="w-full max-w-xl glass rounded-2xl p-4 flex-1 flex flex-col gap-3 min-h-[60vh]">
        <div className="flex-1 overflow-y-auto flex flex-col gap-3">
          {msgs.map((m, i) => (
            <div key={i} className={m.role === "user" ? "self-end max-w-[85%] rounded-2xl rounded-br-sm bg-primary text-primary-foreground px-3 py-2 text-sm" : "self-start max-w-[85%] text-sm text-foreground"}>{m.text}</div>
          ))}
          <div ref={end} />
        </div>
        {state !== "done" && (
          <div className="flex gap-2 items-end">
            <Textarea value={input} onChange={(e) => setInput(e.target.value)} rows={1} placeholder="Antwort…" className="resize-none"
              onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); submit(); } }} />
            <Button size="icon" onClick={submit} disabled={state === "sending"}>{state === "sending" ? <Loader2 className="animate-spin" /> : <ArrowUp />}</Button>
          </div>
        )}
        {state === "done" && <Button asChild variant="secondary"><Link to="/app">Zurück zum Chat</Link></Button>}
      </div>
    </div>
  );
}
