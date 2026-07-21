import client from "prom-client";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const application = "nexus-web";
const metrics = getMetrics();

function getMetrics() {
  const metricsKey = "__nexusMetricsRegister";
  const globalMetrics = globalThis as typeof globalThis & {
    [metricsKey]?: {
      register: client.Registry;
      scrapes: client.Counter;
    };
  };

  if (!globalMetrics[metricsKey]) {
    const registry = new client.Registry();
    registry.setDefaultLabels({ application });
    client.collectDefaultMetrics({ register: registry });

    const scrapes = new client.Counter({
      name: "nextjs_metrics_scrapes_total",
      help: "Total Prometheus scrapes of the Next.js metrics endpoint.",
      registers: [registry],
    });

    globalMetrics[metricsKey] = { register: registry, scrapes };
  }

  return globalMetrics[metricsKey];
}

export async function GET() {
  metrics.scrapes.inc();

  return new Response(await metrics.register.metrics(), {
    headers: {
      "Content-Type": metrics.register.contentType,
      "Cache-Control": "no-store",
    },
  });
}
