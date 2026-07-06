"use client";

import { PlusIcon } from "@/components/ui/plus";
import { DeleteIcon } from "@/components/ui/delete";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export type KV = { key: string; value: string };

/** Convierte un mapa a filas editables (orden estable). */
export function recordToRows(
  record: Record<string, string> | undefined | null,
): KV[] {
  if (!record) return [];
  return Object.entries(record).map(([key, value]) => ({ key, value }));
}

/** Convierte filas a mapa, ignorando claves vacías y normalizando. */
export function rowsToRecord(rows: KV[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const row of rows) {
    const key = row.key.trim();
    if (key) out[key] = row.value;
  }
  return out;
}

/**
 * Editor de pares clave/valor (variables). Filas con add/remove. No envía nada:
 * el padre posee el estado y llama a onChange.
 */
export function KeyValueEditor({
  rows,
  onChange,
  keyPlaceholder = "name",
  valuePlaceholder = "default value",
  addLabel = "Add variable",
  keyTrigger = false,
}: {
  rows: KV[];
  onChange: (rows: KV[]) => void;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
  addLabel?: string;
  /** When true, the key field is decorated as the trigger `{$ … }`: `{$` and `}`
   * render at low opacity and the user writes the name in the middle. */
  keyTrigger?: boolean;
}) {
  function update(index: number, field: keyof KV, value: string) {
    onChange(rows.map((row, i) => (i === index ? { ...row, [field]: value } : row)));
  }

  return (
    <div className="flex flex-col gap-2">
      {rows.map((row, index) => (
        <div key={index} className="flex items-center gap-2">
          {keyTrigger ? (
            <div className="flex h-7 w-full min-w-0 items-center rounded-md border border-input bg-input/20 px-2 font-mono text-xs transition-colors focus-within:border-ring focus-within:ring-2 focus-within:ring-ring/30 dark:bg-input/30">
              <span aria-hidden className="shrink-0 select-none text-muted-foreground/60">
                &#123;$
              </span>
              <input
                className="w-full min-w-0 bg-transparent px-1 text-foreground outline-none placeholder:text-muted-foreground/50"
                placeholder={keyPlaceholder}
                value={row.key}
                onChange={(e) => update(index, "key", e.target.value)}
              />
              <span aria-hidden className="shrink-0 select-none text-muted-foreground/60">
                &#125;
              </span>
            </div>
          ) : (
            <Input
              className="font-mono text-xs"
              placeholder={keyPlaceholder}
              value={row.key}
              onChange={(e) => update(index, "key", e.target.value)}
            />
          )}
          <Input
            className="font-mono text-xs"
            placeholder={valuePlaceholder}
            value={row.value}
            onChange={(e) => update(index, "value", e.target.value)}
          />
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="Remove variable"
            onClick={() => onChange(rows.filter((_, i) => i !== index))}
          >
            <DeleteIcon className="size-3.5" />
          </Button>
        </div>
      ))}
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="w-fit"
        onClick={() => onChange([...rows, { key: "", value: "" }])}
      >
        <PlusIcon size={14} />
        {addLabel}
      </Button>
    </div>
  );
}
