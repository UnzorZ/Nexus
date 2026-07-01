package dev.unzor.nexus.documents.domain.exception;

/** Plantilla de documento inexistente. → 404 resource_not_found. */
public class DocumentTemplateNotFoundException extends RuntimeException {
    public DocumentTemplateNotFoundException(String message) {
        super(message);
    }
}
