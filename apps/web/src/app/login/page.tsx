"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useRef, useState } from "react";
import { motion } from "motion/react";
import { Eye, EyeOff, LogIn } from "lucide-react";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { NexusApiError, apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME, ensureCsrfToken } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";
import { SPRING_SNAPPY } from "@/components/dashboard/anim";

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const continuePath = searchParams.get("continue") ?? "/projects";

  const [error, setError] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsPending(true);

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "").trim();
    const password = String(formData.get("password") ?? "");

    if (!email) {
      setError("Please enter your email address.");
      setIsPending(false);
      return;
    }

    if (!password) {
      setError("Please enter your password.");
      setIsPending(false);
      return;
    }

    try {
      const csrfToken = await ensureCsrfToken();

      await apiClient.post<unknown>(
        apiRoutes.panel.session.loginJson,
        { email, password },
        {
          headers: { [CSRF_HEADER_NAME]: csrfToken },
          redirect: "manual",
          errorMessage: "Invalid email or password.",
        },
      );

      // Session is set server-side via JSESSIONID cookie.
      router.push(continuePath);
      router.refresh();
    } catch (err) {
      if (err instanceof NexusApiError) {
        setError(err.message);
      } else {
        setError("Could not connect to the Nexus API.");
      }
    } finally {
      setIsPending(false);
    }
  }

  return (
    <main className="flex min-h-screen flex-col bg-muted/40">
      <header className="flex h-16 items-center border-b bg-card px-6">
        <div className="flex items-center gap-3">
          <Image
            src="/nexus-logo-icon.png"
            alt="Nexus"
            width={32}
            height={32}
            className="h-8 w-auto"
            priority
          />
          <span className="text-lg font-semibold tracking-tight">NEXUS</span>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-6 py-10">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
        >
          <Card>
            <CardHeader className="text-center">
              <CardTitle className="text-xl">Sign in</CardTitle>
              <CardDescription>
                Enter your Nexus account credentials to continue.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form
                ref={formRef}
                onSubmit={handleSubmit}
                className="flex flex-col gap-5"
              >
                <div className="flex flex-col gap-2">
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    placeholder="marcos@example.com"
                    required
                    disabled={isPending}
                  />
                </div>

                <div className="flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="password">Password</Label>
                  </div>
                  <div className="relative">
                    <Input
                      id="password"
                      name="password"
                      type={showPassword ? "text" : "password"}
                      autoComplete="current-password"
                      placeholder="••••••••"
                      required
                      disabled={isPending}
                      className="pr-10"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      aria-label={showPassword ? "Hide password" : "Show password"}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                      tabIndex={-1}
                    >
                      {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </div>

                {error ? (
                  <motion.p
                    initial={{ opacity: 0, y: -4 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="rounded-md border border-destructive/20 bg-destructive/10 px-3 py-2 text-sm text-destructive"
                  >
                    {error}
                  </motion.p>
                ) : null}

                <Button type="submit" disabled={isPending} className="w-full gap-2">
                  <LogIn size={16} />
                  {isPending ? "Signing in..." : "Sign in"}
                </Button>
              </form>

              <p className="mt-6 text-center text-sm text-muted-foreground">
                Don&apos;t have an account?{" "}
                <Link
                  href="/register"
                  className="font-medium text-primary hover:underline"
                >
                  Create one
                </Link>
              </p>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </main>
  );
}
