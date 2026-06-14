import Link from "next/link";

export default function Home() {
  return (
    <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
      <section className="mx-auto flex min-h-[80vh] max-w-4xl flex-col justify-center">
        <p className="mb-3 font-mono text-xs uppercase tracking-[0.18em] text-[#796d5f]">
          Nexus control plane
        </p>
        <h1 className="max-w-2xl text-5xl font-semibold tracking-tight sm:text-7xl">
          Panel de control Nexus
        </h1>
        <p className="mt-6 max-w-xl text-base leading-7 text-[#5e554a]">
          Crea una cuenta Nexus, inicia sesión con sesión HTTP segura en la API y
          accede al dashboard del panel.
        </p>
        <div className="mt-9 flex flex-wrap gap-3">
          <Link
            href="/login"
            className="inline-flex h-12 items-center border border-[#181612] bg-[#181612] px-5 text-sm font-medium text-[#f7f4ef] transition hover:bg-transparent hover:text-[#181612]"
          >
            Iniciar sesión
          </Link>
          <Link
            href="/register"
            className="inline-flex h-12 items-center border border-[#d8d0c4] bg-white/70 px-5 text-sm font-medium text-[#3d352b] transition hover:border-[#181612]"
          >
            Crear cuenta
          </Link>
          <Link
            href="/dashboard"
            className="inline-flex h-12 items-center border border-transparent px-5 text-sm font-medium text-[#796d5f] underline transition hover:text-[#181612]"
          >
            Ir al dashboard
          </Link>
        </div>
      </section>
    </main>
  );
}
