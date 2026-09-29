"use client";

import { useState } from "react";
import { XIcon } from "@/components/ui/x";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { KNOWN_SCOPES, isValidScope } from "@/features/api-keys/api";

/**
 * Editor de scopes de una API key: campo libre {@code module:action}, atajos
 * con los scopes conocidos y chips para quitar. Lo usan los diálogos de crear
 * y de editar scopes.
 */
export function ScopePicker({
  value,
  onChange,
}: {
  value: string[];
  onChange: (scopes: string[]) => void;
}) {
  const [input, setInput] = useState("");
  const [error, setError] = useState<string | null>(null);

  function add(raw: string) {
    const trimmed = raw.trim();
    if (!trimmed) return;
    if (!isValidScope(trimmed)) {
      setError("Use the format module:action (e.g. registry:heartbeat).");
      return;
    }
    if (!value.includes(trimmed)) onChange([...value, trimmed]);
    setInput("");
    setError(null);
  }

  return (
    <div className="flex flex-col gap-1.5">
      <Label>Scopes</Label>
      <div className="flex gap-2">
        <Input
          placeholder="module:action"
          value={input}
          onChange={(e) => {
            setInput(e.target.value);
            setError(null);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add(input);
            }
          }}
          className="font-mono"
        />
        <Button variant="outline" type="button" onClick={() => add(input)}>
          Add
        </Button>
      </div>
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
      <div className="flex flex-wrap gap-1">
        {KNOWN_SCOPES.filter((s) => !value.includes(s)).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => add(s)}
            className="rounded-full border border-dashed border-border px-2 py-0.5 font-mono text-[10px] text-muted-foreground hover:border-primary/40 hover:text-foreground"
          >
            + {s}
          </button>
        ))}
      </div>
      {value.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {value.map((scope) => (
            <Badge
              key={scope}
              variant="secondary"
              className="gap-1 font-mono text-[11px]"
            >
              {scope}
              <button
                type="button"
                onClick={() => onChange(value.filter((x) => x !== scope))}
                className="text-muted-foreground hover:text-foreground"
                aria-label={`Remove ${scope}`}
              >
                <XIcon size={12} />
              </button>
            </Badge>
          ))}
        </div>
      ) : null}
    </div>
  );
}
