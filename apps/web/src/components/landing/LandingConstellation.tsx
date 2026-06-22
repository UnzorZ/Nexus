"use client";

import { useEffect, useRef } from "react";

/**
 * The hero constellation: a pulsing central Nexus core woven together with
 * drifting nodes by glowing threads, ringed by three faint interlocking
 * guides (a nod to the logo). Purely abstract — no labels — so it reads as
 * atmosphere rather than a diagram. Gentle parallax follows the pointer on
 * devices that have a precise hover; touch devices stay still.
 *
 * Canvas2D, additive glow, rAF, reduced-motion aware. Sits behind the hero
 * text and never captures pointer events (parallax is driven from a window
 * listener), so all copy stays selectable/clickable.
 */

type Node = {
  label?: string;
  baseAngle: number;
  ring: number; // fraction of field radius
  speed: number;
  size: number;
  primary: boolean;
};

// Capability/module names only — no example projects (those live in your data,
// not on the marketing page).
const MODULES = [
  "Identity",
  "Permissions",
  "API keys",
  "Registry",
  "Audit",
  "Notify",
];

function buildNodes(): Node[] {
  const nodes: Node[] = [];
  const primary = MODULES.length;
  for (let i = 0; i < primary; i++) {
    nodes.push({
      label: MODULES[i],
      baseAngle: (i / primary) * Math.PI * 2 - Math.PI / 2,
      ring: 0.74,
      speed: 0.016 + 0.01 * Math.cos(i * 7.3),
      size: 3.6,
      primary: true,
    });
  }
  const sat = 9;
  for (let i = 0; i < sat; i++) {
    nodes.push({
      baseAngle: (i / sat) * Math.PI * 2 + 0.5,
      ring: 1.06 + 0.07 * Math.sin(i * 3.7),
      speed: 0.01 + 0.006 * Math.sin(i * 5.1),
      size: 1.7,
      primary: false,
    });
  }
  return nodes;
}

export function LandingConstellation() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d", { alpha: true });
    if (!ctx) return;

    const nodes = buildNodes();
    const reduce = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    const canHover = window.matchMedia("(hover: hover)").matches;

    // Pointer parallax target (normalized -1..1 from window center).
    const ptr = { x: 0, y: 0 };
    const par = { x: 0, y: 0 }; // lerped current offset

    let fontStack = "system-ui, sans-serif";
    const rs = getComputedStyle(document.documentElement);
    const fv = rs.getPropertyValue("--font-sans");
    if (fv) fontStack = `${fv.trim()}, system-ui, sans-serif`;

    let w = 0;
    let h = 0;
    let dpr = 1;

    const resize = () => {
      const cw = canvas.clientWidth || 1;
      const ch = canvas.clientHeight || 1;
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = cw;
      h = ch;
      canvas.width = Math.floor(cw * dpr);
      canvas.height = Math.floor(ch * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    const draw = (time: number) => {
      ctx.clearRect(0, 0, w, h);

      const wide = w > 820;
      const cx = w * (wide ? 0.66 : 0.5);
      const cy = h * 0.5;
      const fieldR = Math.min(w, h) * (wide ? 0.34 : 0.3);

      if (canHover && !reduce) {
        par.x += (ptr.x - par.x) * 0.06;
        par.y += (ptr.y - par.y) * 0.06;
      }
      const ox = par.x * fieldR * 0.09;
      const oy = par.y * fieldR * 0.09;

      const pos = nodes.map((n, i) => {
        const ang = n.baseAngle + time * n.speed;
        const bob = Math.sin(time * 0.6 + i * 1.7) * fieldR * 0.025;
        const r = n.ring * fieldR + bob;
        const depth = (n.ring - 0.7) * 1.6;
        const x = cx + Math.cos(ang) * r + ox * (1 + depth);
        const y = cy + Math.sin(ang) * r + oy * (1 + depth);
        return { x, y, n };
      });

      ctx.globalCompositeOperation = "lighter";

      // Central halo.
      const halo = ctx.createRadialGradient(cx, cy, 0, cx, cy, fieldR * 1.15);
      halo.addColorStop(0, "rgba(109, 77, 255, 0.22)");
      halo.addColorStop(0.5, "rgba(76, 70, 200, 0.06)");
      halo.addColorStop(1, "rgba(0, 0, 0, 0)");
      ctx.fillStyle = halo;
      ctx.beginPath();
      ctx.arc(cx, cy, fieldR * 1.15, 0, Math.PI * 2);
      ctx.fill();

      // Faint interlocking guide rings.
      ctx.lineWidth = 1;
      const rings = [0.5, 0.72, 0.94];
      rings.forEach((rr, idx) => {
        ctx.strokeStyle = `rgba(150, 160, 220, ${0.05 - idx * 0.008})`;
        ctx.beginPath();
        ctx.arc(cx, cy, fieldR * rr, 0, Math.PI * 2);
        ctx.stroke();
      });

      // Node → adjacent node (woven ring), primaries only.
      const primary = pos.filter((p) => p.n.primary);
      ctx.lineWidth = 1;
      for (let i = 0; i < primary.length; i++) {
        const a = primary[i];
        const b = primary[(i + 1) % primary.length];
        ctx.strokeStyle = "rgba(120, 110, 220, 0.07)";
        ctx.beginPath();
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
        ctx.stroke();
      }

      // Core → node threads (primary links).
      const pulse = 0.5 + 0.5 * Math.sin(time * 1.4);
      pos.forEach((p) => {
        if (!p.n.primary) return;
        const g = ctx.createLinearGradient(cx, cy, p.x, p.y);
        g.addColorStop(0, `rgba(109, 77, 255, ${0.3 + pulse * 0.06})`);
        g.addColorStop(1, "rgba(76, 217, 230, 0.02)");
        ctx.strokeStyle = g;
        ctx.lineWidth = 1.2;
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.lineTo(p.x, p.y);
        ctx.stroke();
      });

      // Nodes.
      pos.forEach((p) => {
        const col = p.n.primary
          ? "rgba(76, 217, 230, 0.95)"
          : "rgba(150, 140, 240, 0.6)";
        ctx.shadowColor = col;
        ctx.shadowBlur = p.n.primary ? 16 : 9;
        ctx.fillStyle = col;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.n.size, 0, Math.PI * 2);
        ctx.fill();
      });
      ctx.shadowBlur = 0;

      // Core.
      const coreR = fieldR * 0.09 * (1 + pulse * 0.12);
      const coreGlow = ctx.createRadialGradient(cx, cy, 0, cx, cy, coreR * 4);
      coreGlow.addColorStop(0, "rgba(190, 170, 255, 0.95)");
      coreGlow.addColorStop(0.25, "rgba(109, 77, 255, 0.55)");
      coreGlow.addColorStop(1, "rgba(109, 77, 255, 0)");
      ctx.fillStyle = coreGlow;
      ctx.beginPath();
      ctx.arc(cx, cy, coreR * 4, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = "rgba(235, 230, 255, 0.98)";
      ctx.shadowColor = "rgba(109, 77, 255, 0.9)";
      ctx.shadowBlur = 30;
      ctx.beginPath();
      ctx.arc(cx, cy, coreR, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      ctx.globalCompositeOperation = "source-over";

      // Module labels — capability names only, roomy screens.
      if (wide) {
        ctx.font = `500 11.5px ${fontStack}`;
        ctx.textBaseline = "middle";
        pos
          .filter((p) => p.n.primary && p.n.label)
          .forEach((p) => {
            const label = p.n.label as string;
            const dx = p.x - cx;
            const dy = p.y - cy;
            const dist = Math.hypot(dx, dy) || 1;
            const lx = p.x + (dx / dist) * 15;
            const ly = p.y + (dy / dist) * 15;
            ctx.fillStyle = "rgba(190, 205, 235, 0.8)";
            ctx.textAlign = dx >= 0 ? "left" : "right";
            ctx.fillText(label, lx, ly);
          });
      }
    };

    let raf = 0;
    const start = performance.now();
    const loop = () => {
      draw((performance.now() - start) / 1000);
      raf = requestAnimationFrame(loop);
    };

    const onPointer = (e: PointerEvent) => {
      if (e.pointerType === "touch") return;
      ptr.x = (e.clientX / window.innerWidth) * 2 - 1;
      ptr.y = (e.clientY / window.innerHeight) * 2 - 1;
    };

    resize();
    if (reduce) {
      draw(2.5);
    } else {
      raf = requestAnimationFrame(loop);
      window.addEventListener("pointermove", onPointer, { passive: true });
    }

    const ro = new ResizeObserver(() => {
      resize();
      if (reduce) draw(2.5);
    });
    ro.observe(canvas);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("pointermove", onPointer);
      ro.disconnect();
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="landing-constellation"
      aria-hidden="true"
    />
  );
}
