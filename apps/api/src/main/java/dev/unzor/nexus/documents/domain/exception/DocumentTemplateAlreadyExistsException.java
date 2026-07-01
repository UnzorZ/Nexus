package dev.unzor.nexus.documents.domain.exception;

/** Ya existe una plantilla con ese nombre en el proyecto. → 409 conflict. */
public class DocumentTemplateAlreadyExistsException extends RuntimeException {
    public DocumentTemplateAlreadyExistsException(String message) {
        super(message);
    }
}
