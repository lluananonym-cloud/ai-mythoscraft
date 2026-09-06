import { useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Check, Copy, Download, Play, FileArchive } from "lucide-react";
import JSZip from "jszip";
import { toast } from "sonner";

const EXT: Record<string, string> = {
  html: "html", xml: "xml", javascript: "js", js: "js", jsx: "jsx", typescript: "ts", ts: "ts",
  tsx: "tsx", python: "py", py: "py", java: "java", json: "json", css: "css", scss: "scss",
  bash: "sh", sh: "sh", shell: "sh", yaml: "yml", yml: "yml", sql: "sql", md: "md",
  markdown: "md", csv: "csv", php: "php", go: "go", rust: "rs", rs: "rs", c: "c", cpp: "cpp",
  cs: "cs", kotlin: "kt", swift: "swift", toml: "toml", ini: "ini", txt: "txt",
};

function download(name: string, content: string, type = "text/plain") {
  const url = URL.createObjectURL(new Blob([content], { type: `${type};charset=utf-8` }));
  const a = document.createElement("a");
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

function IconBtn({ onClick, title, children }: { onClick: () => void; title: string; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      aria-label={title}
      className="rounded-md border border-white/10 bg-background/60 p-1.5 text-muted-foreground backdrop-blur transition-colors hover:text-foreground hover:bg-white/10"
    >
      {children}
    </button>
  );
}

/** Extract fenced blocks from raw markdown, for the "alles als ZIP" action. */
function extractBlocks(md: string) {
  const out: { lang: string; code: string; name: string }[] = [];
  const re = /```([a-zA-Z0-9+#._-]*)[^\n]*\n([\s\S]*?)```/g;
  let m: RegExpExecArray | null;
  let i = 1;
  while ((m = re.exec(md))) {
    const lang = (m[1] || "txt").toLowerCase();
    const named = /^(?:\/\/|#|<!--)\s*([\w.\-/]+\.[a-zA-Z0-9]{1,5})/.exec(m[2].trim());
    out.push({
      lang,
      code: m[2],
      name: named?.[1] || `datei-${i}.${EXT[lang] || "txt"}`,
    });
    i++;
  }
  return out;
}

function CodeCard({ lang, code }: { lang: string; code: string }) {
  const [copied, setCopied] = useState(false);
  const ext = EXT[lang] || "txt";
  const isHtml = lang === "html" || /^\s*<(!doctype|html)/i.test(code);

  const copy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };

  const run = () => {
    const w = window.open("", "_blank");
    if (!w) { toast.error("Popup blockiert — bitte erlauben"); return; }
    w.document.open(); w.document.write(code); w.document.close();
  };

  return (
    <div className="not-prose group/code relative my-3 overflow-hidden rounded-xl border border-white/10 bg-background/50">
      <div className="flex items-center gap-2 border-b border-white/5 px-3 py-1.5">
        <span className="text-[11px] uppercase tracking-wider text-muted-foreground">{lang || "code"}</span>
        <div className="ml-auto flex items-center gap-1">
          {isHtml && (
            <IconBtn onClick={run} title="HTML ausführen"><Play className="h-3.5 w-3.5" /></IconBtn>
          )}
          <IconBtn onClick={() => download(`mythos-code.${ext}`, code)} title="Als Datei herunterladen">
            <Download className="h-3.5 w-3.5" />
          </IconBtn>
          <IconBtn onClick={copy} title="Kopieren">
            {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
          </IconBtn>
        </div>
      </div>
      <pre className="overflow-x-auto p-3 text-[13px] leading-relaxed"><code>{code}</code></pre>
    </div>
  );
}

export default function MarkdownMessage({ content }: { content: string }) {
  const blocks = useMemo(() => extractBlocks(content), [content]);

  const zipAll = async () => {
    const zip = new JSZip();
    blocks.forEach((b, i) => zip.file(b.name.includes(".") ? b.name : `datei-${i + 1}.txt`, b.code));
    zip.file("antwort.md", content);
    const blob = await zip.generateAsync({ type: "blob" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = "mythos-dateien.zip";
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
    toast.success("ZIP heruntergeladen");
  };

  return (
    <>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          pre: ({ children }) => <>{children}</>,
          code: ({ className, children, ...props }: any) => {
            const raw = String(children ?? "");
            const lang = /language-(\w+)/.exec(className || "")?.[1] || "";
            const isBlock = lang || raw.includes("\n");
            if (!isBlock) {
              return <code className="rounded bg-white/10 px-1.5 py-0.5 font-mono text-[13px]" {...props}>{children}</code>;
            }
            return <CodeCard lang={lang} code={raw.replace(/\n$/, "")} />;
          },
        }}
      >
        {content}
      </ReactMarkdown>

      {blocks.length > 1 && (
        <div className="not-prose mt-2">
          <button
            type="button"
            onClick={zipAll}
            className="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] text-muted-foreground transition-colors hover:text-foreground hover:bg-white/10"
          >
            <FileArchive className="h-3.5 w-3.5" /> Alle {blocks.length} Dateien als ZIP
          </button>
        </div>
      )}
    </>
  );
}
