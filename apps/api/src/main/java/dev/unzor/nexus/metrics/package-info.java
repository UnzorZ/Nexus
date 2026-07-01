@org.springframework.modulith.ApplicationModule(displayName = "Metrics")
package dev.unzor.nexus.metrics;

/**
 * Serie temporal de métricas por proyecto (sólo append). Las apps reportan
 * puntos desde el API de proyecto ({@code /api/v1/metrics}); el panel los
 * consulta agregados por nombre.
 */
