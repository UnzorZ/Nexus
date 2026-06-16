import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  {
    // Vendored shadcn/ui components (generated via `shadcn add`) lean on
    // `any` in places; exclude them from the no-explicit-any rule.
    files: ["src/components/ui/**/*.{ts,tsx}"],
    rules: {
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-unused-vars": "off",
    },
  },
  {
    // The dashboard drives animated icons from their container via
    // `animHandlers(ref)` — a helper that only reads the ref inside event
    // callbacks (safe). The `react-hooks/refs` rule can't see through the
    // function boundary and flags passing the ref as an arg, so relax it here.
    files: ["src/components/dashboard/**/*.{ts,tsx}", "src/app/dashboard/**/*.{ts,tsx}"],
    rules: {
      "react-hooks/refs": "off",
    },
  },
]);

export default eslintConfig;
