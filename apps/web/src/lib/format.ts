const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

/**
 * "42 seconds ago" / "4 minutes ago" / "3 hours ago" / "2 days ago".
 * Devuelve el valor de entrada si no es una fecha válida.
 */
export function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return iso;
  const secondsAgo = Math.round((then - Date.now()) / 1000);
  const abs = Math.abs(secondsAgo);
  if (abs < 60) return rtf.format(secondsAgo, "second");
  if (abs < 3600) return rtf.format(Math.round(secondsAgo / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(secondsAgo / 3600), "hour");
  return rtf.format(Math.round(secondsAgo / 86400), "day");
}
