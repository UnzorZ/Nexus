import Link from "next/link";

export default async function LoginErrorPage({
  searchParams,
}: {
  searchParams: Promise<{ message?: string }>;
}) {
  const { message } = await searchParams;

  return (
    <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
      <section className="mx-auto flex min-h-[80vh] max-w-3xl flex-col justify-center">
        <p className="mb-3 font-mono text-xs uppercase tracking-[0.18em] text-[#796d5f]">
          Nexus identity
        </p>
        <h1 className="max-w-xl text-4xl font-semibold tracking-tight sm:text-5xl">
          Sign in could not be completed
        </h1>
        <p className="mt-5 max-w-xl text-base leading-7 text-[#5e554a]">
          {message ?? "The authorization callback was invalid."}
        </p>
        <div className="mt-9">
          <Link
            href="/login"
            className="inline-flex h-12 items-center border border-[#181612] bg-[#181612] px-5 text-sm font-medium text-[#f7f4ef] transition hover:bg-transparent hover:text-[#181612]"
          >
            Try again
          </Link>
        </div>
      </section>
    </main>
  );
}
