@org.springframework.modulith.ApplicationModule(displayName = "Documents")
package dev.unzor.nexus.documents;

/**
 * Generación de documentos por plantilla. El panel gestiona plantillas
 * ({@code {{var}}} sustitución); las apps renderizan desde el API de proyecto
 * ({@code /api/v1/documents}).
 */
