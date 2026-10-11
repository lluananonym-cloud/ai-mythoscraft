import { useEffect, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Check, Copy, Download, Play, FileArchive, Eye, EyeOff, FileText, FileCode2, FileImage, FileSpreadsheet, ImageDown } from "lucide-react";
import JSZip from "jszip";
import { toast } from "sonner";
import { copyText } from "@/lib/copyText";
import { parseFileInfo, fileBlob, saveBlob, openBlob, svgToPng, fmtSize, extOf, isImageName } from "@/lib/chatFiles";

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
      className="rounded-md border border-white/10 bg-background/60 p-2 sm:p-1.5 touch-manipulation text-muted-foreground backdrop-blur transition-colors hover:text-foreground hover:bg-white/10"
    >
      {children}
    </button>
  );
}

/** Extract fenced blocks from raw markdown, for the "alles als ZIP" action. */
function extractBlocks(md: string) {
  const out: { lang: string; code: string; name: string; base64: boolean; isFile: boolean }[] = [];
  const re = /```([^\s`]*)[^\n]*\n([\s\S]*?)```/g;
  let m: RegExpExecArray | null;
  let i = 1;
  while ((m = re.exec(md))) {
    const file = parseFileInfo(m[1]);
    const lang = (m[1] || "txt").toLowerCase();
    const named = /^(?:\/\/|#|<!--)\s*([\w.\-/]+\.[a-zA-Z0-9]{1,5})/.exec(m[2].trim());
    out.push({
      lang,
      code: m[2].replace(/\n$/, ""),
      name: file?.name || named?.[1] || `datei-${i}.${EXT[lang] || "txt"}`,
      base64: !!file?.base64,
      isFile: !!file,
    });
    i++;
  }
  return out;
}

const fileIcon = (name: string) => {
  const e = extOf(name);
  if (isImageName(name)) return FileImage;
  if (e === "csv" || e === "tsv" || e === "xlsx") return FileSpreadsheet;
  if (e === "md" || e === "txt" || e === "pdf" || e === "docx") return FileText;
  return FileCode2;
};

/** Eine Datei, die Mythos geschickt hat: Name, Größe, Download, Vorschau. */
function FileCard({ name, content, base64 }: { name: string; content: string; base64: boolean }) {
  const [show, setShow] = useState(false);
  const [copied, setCopied] = useState(false);
  const blob = useMemo(() => { try { return fileBlob(name, content, base64); } catch { return null; } }, [name, content, base64]);
  const preview = useMemo(() => (show && blob && isImageName(name) ? URL.createObjectURL(blob) : null), [show, blob, name]);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);
  const e = extOf(name);
  const Icon = fileIcon(name);
  const canOpen = e === "html" || e === "htm" || e === "pdf" || isImageName(name);

  const copy = async () => {
    if (!(await copyText(content))) { toast.error("Kopieren hat nicht geklappt."); return; }
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };
  const asPng = async () => {
    try { saveBlob(name.replace(/\.svg$/i, ".png"), await svgToPng(content)); toast.success("PNG gespeichert"); }
    catch { toast.error("Konnte kein PNG daraus machen."); }
  };

  return (
    <div className="not-prose my-3 overflow-hidden rounded-xl border border-primary/25 bg-primary/5">
      <div className="flex items-center gap-3 px-3 py-2.5">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/15 text-primary"><Icon className="h-[18px] w-[18px]" /></div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium" title={name}>{name}</div>
          <div className="text-[11px] text-muted-foreground">{blob ? `${e.toUpperCase() || "Datei"} · ${fmtSize(blob.size)}` : "Datei ist beschädigt"}</div>
        </div>
        <div className="flex items-center gap-1">
          {!base64 && <IconBtn onClick={() => setShow(s => !s)} title={show ? "Vorschau schließen" : "Vorschau"}>{show ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}</IconBtn>}
          {base64 && isImageName(name) && <IconBtn onClick={() => setShow(s => !s)} title="Vorschau"><Eye className="h-3.5 w-3.5" /></IconBtn>}
          {canOpen && blob && <IconBtn onClick={() => openBlob(blob)} title="In neuem Tab öffnen"><Play className="h-3.5 w-3.5" /></IconBtn>}
          {e === "svg" && !base64 && <IconBtn onClick={asPng} title="Als PNG speichern"><ImageDown className="h-3.5 w-3.5" /></IconBtn>}
          {!base64 && <IconBtn onClick={copy} title="Inhalt kopieren">{copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}</IconBtn>}
          <button
            type="button"
            disabled={!blob}
            onClick={() => blob && saveBlob(name, blob)}
            className="ml-1 inline-flex items-center gap-1.5 rounded-lg bg-primary px-2.5 py-1.5 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-40"
          >
            <Download className="h-3.5 w-3.5" /> Herunterladen
          </button>
        </div>
      </div>
      {show && (
        preview
          ? <div className="border-t border-white/5 bg-background/40 p-3"><img src={preview} alt={name} className="mx-auto max-h-80 rounded" /></div>
          : <pre className="max-h-80 overflow-auto border-t border-white/5 bg-background/50 p-3 text-[12.5px] leading-relaxed"><code>{content}</code></pre>
      )}
    </div>
  );
}

function CodeCard({ lang, code }: { lang: string; code: string }) {
  const [copied, setCopied] = useState(false);
  const ext = EXT[lang] || "txt";
  const isHtml = lang === "html" || /^\s*<(!doctype|html)/i.test(code);

  const copy = async () => {
    if (!(await copyText(code))) { toast.error("Kopieren hat nicht geklappt. Halte den Code gedrückt und kopiere ihn von Hand."); return; }
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };

  const run = () => {
    const url = URL.createObjectURL(new Blob([code], { type: "text/html;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url; a.target = "_blank"; a.rel = "noopener";
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  return (
    <div className="not-prose group/code relative my-3 overflow-hidden rounded-xl border border-white/10 bg-background/50">
      <div className="flex items-center gap-2 border-b border-white/5 px-3 py-1.5">
        <span className="text-[11px] uppercase tracking-wider text-muted-foreground">{lang || "code"}</span>
        <div className="ml-auto flex items-center gap-1">
          {isHtml && (
            <IconBtn onClick={run} title="HTML ausführen"><Play className="h-3.5 w-3.5" /></IconBtn>
          )}
          <IconBtn onClick={() => download(/^(?:\/\/|#|<!--)\s*([\w.-]+\.[a-zA-Z0-9]{1,5})/.exec(code.trim())?.[1] || `mythos-code.${ext}`, code)} title="Als Datei herunterladen">
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
  // Hat Mythos echte Dateien geschickt, kommen nur die ins ZIP; sonst alle Codeblöcke + die Antwort.
  const hasFiles = blocks.some(b => b.isFile);
  const zipFiles = hasFiles ? blocks.filter(b => b.isFile) : blocks;

  const zipAll = async () => {
    const zip = new JSZip();
    zipFiles.forEach((b, i) => {
      const name = b.name.includes(".") ? b.name : `datei-${i + 1}.txt`;
      if (b.base64) { try { zip.file(name, b.code.replace(/^data:[^,]*,/, "").replace(/\s+/g, ""), { base64: true }); } catch { /* kaputt, weglassen */ } }
      else zip.file(name, b.code);
    });
    if (!hasFiles) zip.file("antwort.md", content);
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
            const info = /language-(\S+)/.exec(className || "")?.[1] || "";
            const file = parseFileInfo(info);
            if (file) return <FileCard name={file.name} content={raw.replace(/\n$/, "")} base64={file.base64} />;
            const lang = /^[\w+#-]+/.exec(info)?.[0] || "";
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

      {zipFiles.length > 1 && (
        <div className="not-prose mt-2">
          <button
            type="button"
            onClick={zipAll}
            className="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] text-muted-foreground transition-colors hover:text-foreground hover:bg-white/10"
          >
            <FileArchive className="h-3.5 w-3.5" /> Alle {zipFiles.length} Dateien als ZIP
          </button>
        </div>
      )}
    </>
  );
}
