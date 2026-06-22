"use client";

import { useEffect, useRef } from "react";

/**
 * Full-bleed WebGL aurora behind the landing hero.
 *
 * A domain-warped FBM flow-field in the brand's violet/indigo/cyan, with a
 * faint moving dot-grid (echoing the auth brand grid), an offset central
 * "violet sun", vignette, and a touch of dither to kill banding. All browser
 * access lives inside useEffect; animation state stays in refs/closures.
 *
 * If WebGL is unavailable the canvas stays transparent and the CSS radial
 * gradient fallback on `.landing-root` shows through instead.
 */

const VERT = `
attribute vec2 aPos;
varying vec2 vUv;
void main() {
  vUv = aPos * 0.5 + 0.5;
  gl_Position = vec4(aPos, 0.0, 1.0);
}`;

const FRAG = `
precision highp float;
varying vec2 vUv;
uniform vec2 uRes;
uniform float uTime;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 345.45));
  p += dot(p, p + 34.345);
  return fract(p.x * p.y);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  float a = hash(i);
  float b = hash(i + vec2(1.0, 0.0));
  float c = hash(i + vec2(0.0, 1.0));
  float d = hash(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 6; i++) {
    v += a * noise(p);
    p = p * 2.02 + vec2(1.7, 9.2);
    a *= 0.5;
  }
  return v;
}

void main() {
  vec2 frag = gl_FragCoord.xy;
  float mn = min(uRes.x, uRes.y);
  vec2 p = (frag * 2.0 - uRes) / mn;

  float t = uTime * 0.045;

  // Domain-warped fbm → slow flowing aurora.
  vec2 q = vec2(fbm(p * 0.9 + vec2(t, t * 0.6)),
                fbm(p * 0.9 + vec2(-t * 0.7, t * 0.8)));
  vec2 r = vec2(fbm(p * 0.9 + 1.6 * q + vec2(3.1, 1.7) + t * 0.5),
                fbm(p * 0.9 + 1.6 * q + vec2(8.3, 2.8) - t * 0.4));
  float f = fbm(p * 0.9 + 2.4 * r);

  vec3 col = vec3(0.018, 0.022, 0.045);                 // near-black ink
  col = mix(col, vec3(0.055, 0.06, 0.16), smoothstep(0.15, 0.95, f));        // indigo
  col = mix(col, vec3(0.27, 0.16, 0.62), pow(clamp(f, 0.0, 1.0), 2.0) * 0.85); // violet body
  col += vec3(0.42, 0.14, 0.62) * pow(max(0.0, f - 0.5), 2.2) * 0.9;          // magenta ridges
  col += vec3(0.16, 0.62, 0.86) * smoothstep(0.62, 0.95, r.y) * 0.42;         // cyan highlights

  // Central violet "sun", nudged right toward the constellation.
  float d = length(p - vec2(0.5, -0.05));
  col += vec3(0.40, 0.28, 0.95) * exp(-d * 1.7) * 0.55;
  col += vec3(0.20, 0.55, 0.95) * exp(-d * 3.6) * 0.40;

  // Faint dot grid (brand echo).
  vec2 gp = frag / mn * 46.0;
  float dd = length(fract(gp) - 0.5);
  col += vec3(0.5, 0.52, 0.78) * smoothstep(0.18, 0.0, dd) * 0.012;

  // Vignette.
  col *= 1.0 - 0.6 * pow(length(p * vec2(0.85, 1.0)), 1.7);

  // Dither.
  col += (hash(frag + uTime) - 0.5) * 0.012;

  gl_FragColor = vec4(col, 1.0);
}`;

function compile(gl: WebGLRenderingContext, type: number, src: string) {
  const sh = gl.createShader(type);
  if (!sh) return null;
  gl.shaderSource(sh, src);
  gl.compileShader(sh);
  if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
    gl.deleteShader(sh);
    return null;
  }
  return sh;
}

export function LandingAurora() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const canvas = canvasRef.current;
    if (!canvas) return;

    const gl = canvas.getContext("webgl", {
      antialias: false,
      alpha: true,
      premultipliedAlpha: false,
      powerPreference: "high-performance",
    }) as WebGLRenderingContext | null;
    if (!gl) return; // CSS fallback on .landing-root remains.

    let program: WebGLProgram | null = null;
    let uTime: WebGLUniformLocation | null = null;
    let uRes: WebGLUniformLocation | null = null;
    let buffer: WebGLBuffer | null = null;

    const build = () => {
      const vs = compile(gl, gl.VERTEX_SHADER, VERT);
      const fs = compile(gl, gl.FRAGMENT_SHADER, FRAG);
      if (!vs || !fs) return false;
      const prog = gl.createProgram();
      if (!prog) return false;
      gl.attachShader(prog, vs);
      gl.attachShader(prog, fs);
      gl.linkProgram(prog);
      if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
        gl.deleteProgram(prog);
        return false;
      }
      program = prog;
      gl.useProgram(prog);
      uTime = gl.getUniformLocation(prog, "uTime");
      uRes = gl.getUniformLocation(prog, "uRes");

      // Fullscreen triangle (covers clip space).
      buffer = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
      gl.bufferData(
        gl.ARRAY_BUFFER,
        new Float32Array([-1, -1, 3, -1, -1, 3]),
        gl.STATIC_DRAW,
      );
      const aPos = gl.getAttribLocation(prog, "aPos");
      gl.enableVertexAttribArray(aPos);
      gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);
      return true;
    };

    if (!build()) return;

    const resize = () => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      const scale = 0.7; // render below 1:1 — aurora is soft, gains perf
      const bw = Math.max(2, Math.floor(w * dpr * scale));
      const bh = Math.max(2, Math.floor(h * dpr * scale));
      if (canvas.width !== bw || canvas.height !== bh) {
        canvas.width = bw;
        canvas.height = bh;
      }
      canvas.style.width = `${w}px`;
      canvas.style.height = `${h}px`;
      gl.viewport(0, 0, canvas.width, canvas.height);
    };

    const reduce = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;

    const draw = (time: number) => {
      if (!program) return;
      gl.uniform1f(uTime, time);
      gl.uniform2f(uRes, canvas.width, canvas.height);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    };

    let raf = 0;
    const loop = (ms: number) => {
      if (!program) return;
      draw(ms / 1000);
      raf = requestAnimationFrame(loop);
    };

    resize();
    if (reduce) {
      draw(8); // a pleasing frozen state
    } else {
      raf = requestAnimationFrame(loop);
    }

    const onResize = () => {
      resize();
      if (reduce) draw(8);
    };
    window.addEventListener("resize", onResize, { passive: true });

    const onLost = (e: Event) => {
      e.preventDefault();
      cancelAnimationFrame(raf);
      program = null;
    };
    const onRestored = () => {
      if (build()) {
        resize();
        if (reduce) draw(8);
        else raf = requestAnimationFrame(loop);
      }
    };
    canvas.addEventListener("webglcontextlost", onLost);
    canvas.addEventListener("webglcontextrestored", onRestored);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", onResize);
      canvas.removeEventListener("webglcontextlost", onLost);
      canvas.removeEventListener("webglcontextrestored", onRestored);
      if (program) gl.deleteProgram(program);
      if (buffer) gl.deleteBuffer(buffer);
    };
  }, []);

  return <canvas ref={canvasRef} className="landing-bg" aria-hidden="true" />;
}
