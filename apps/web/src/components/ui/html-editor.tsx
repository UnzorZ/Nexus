"use client";

import { useRef } from "react";
import { cn } from "@/lib/utils";

/**
 * Mini editor de texto con resaltado de HTML. Tamaño fijo + scrollbar interna,
 * así el contenido largo no deforma el contenedor. La técnica es el clásico
 * overlay: un <pre> con el HTML tokenizado y coloreado detrás, y un <textarea>
 * transparente (sólo el caret) delante; el scroll se sincroniza.
 *
 * No es un validador completo — sólo colorea tags, atributos y strings para que
 * se vea claro qué es HTML. El contenido no-HTML se muestra plano.
 */

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function highlightTag(raw: string): string {
  let t = escapeHtml(raw);
  // valores de atributo ("…")
  t = t.replace(
    /(&quot;[^&]*?&quot;)/g,
    '<span class="text-emerald-600 dark:text-emerald-400">$1</span>',
  );
  // nombre de tag tras &lt; o &lt;/
  t = t.replace(
    /(&lt;\/?)([a-zA-Z][\w:-]*)/,
    '$1<span class="text-violet-600 dark:text-violet-300">$2</span>',
  );
  // corchetes / puntuación del tag
  return `<span class="text-sky-600 dark:text-sky-400">${t}</span>`;
}

function highlightHtml(src: string): string {
  if (!src) return "&nbsp;";
  let out = "";
  const re = /(<!--[\s\S]*?-->)|(<[!/]?[a-zA-Z][^>]*>)|([^<]+)|([\s\S])/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) {
    if (m[1]) {
      out += `<span class="italic text-muted-foreground">${escapeHtml(m[1])}</span>`;
    } else if (m[2]) {
      out += highlightTag(m[2]);
    } else if (m[3] !== undefined) {
      out += escapeHtml(m[3]);
    } else if (m[4] !== undefined) {
      out += escapeHtml(m[4]); // un '<' suelto
    }
  }
  return out;
}

export function HtmlEditor({
  value,
  onChange,
  textareaId,
  className,
}: {
  value: string;
  onChange: (value: string) => void;
  textareaId?: string;
  className?: string;
}) {
  const preRef = useRef<HTMLPreElement>(null);
  const taRef = useRef<HTMLTextAreaElement>(null);

  function syncScroll() {
    if (preRef.current && taRef.current) {
      preRef.current.scrollTop = taRef.current.scrollTop;
      preRef.current.scrollLeft = taRef.current.scrollLeft;
    }
  }

  // Métricas idénticas en pre y textarea para que el overlay calce.
  const metrics =
    "m-0 whitespace-pre-wrap break-words p-3 font-mono text-xs leading-relaxed";

  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-md border border-input bg-background",
        className,
      )}
    >
      <pre
        ref={preRef}
        aria-hidden
        className={`pointer-events-none absolute inset-0 overflow-auto ${metrics}`}
      >
        <code dangerouslySetInnerHTML={{ __html: highlightHtml(value) }} />
      </pre>
      <textarea
        id={textareaId}
        ref={taRef}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onScroll={syncScroll}
        spellCheck={false}
        className={`absolute inset-0 h-full w-full resize-none bg-transparent text-transparent caret-foreground outline-none ${metrics}`}
      />
    </div>
  );
}
