import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { NEXUS_SESSION_COOKIE } from "@/lib/oidc-server";

export default async function Dashboard() {
  const cookieStore = await cookies();
  const session = cookieStore.get(NEXUS_SESSION_COOKIE);

  if (!session) {
    redirect("/login");
  }

  return (
    <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
      <section className="mx-auto flex min-h-[80vh] max-w-4xl flex-col justify-center">
        <div className="mb-12 flex items-center justify-between gap-4">
          <p className="font-mono text-xs uppercase tracking-[0.18em] text-[#796d5f]">
            Nexus control plane
          </p>
          <form action="/logout" method="post">
            <button
              type="submit"
              className="rounded-full border border-[#d8d0c4] bg-white/70 px-4 py-2 text-sm font-medium text-[#3d352b] shadow-sm transition hover:border-[#181612] hover:bg-[#181612] hover:text-[#f7f4ef]"
            >
              Cerrar sesión
            </button>
          </form>
        </div>
        <h1 className="max-w-2xl text-5xl font-semibold tracking-tight sm:text-7xl">
          Dashboard
        </h1>
        <p className="mt-6 max-w-xl text-base leading-7 text-[#5e554a]">
          You are signed in through the local Nexus OIDC flow.
        </p>
      </section>
    </main>
  );
}
