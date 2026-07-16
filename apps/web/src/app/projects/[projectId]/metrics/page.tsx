"use client";

import { GaugeIcon } from "@/components/ui/gauge";
import { ModulePage } from "@/components/dashboard/ModulePage";
import { MetricsModule } from "@/components/dashboard/modules/metrics";

/**
 * Metrics — acceso top-level (sidebar) a las métricas push del proyecto. Las
 * series se grafican según llegan (POST /api/v1/metrics/record). Reutiliza el
 * mismo visor que /modules/metrics.
 */
export default function ProjectMetricsPage() {
  return (
    <ModulePage
      moduleKey="metrics"
      title="Metrics"
      description="Usage metrics your apps push via the SDK. Series are charted as points arrive."
      Icon={GaugeIcon}
    >
      <MetricsModule />
    </ModulePage>
  );
}
