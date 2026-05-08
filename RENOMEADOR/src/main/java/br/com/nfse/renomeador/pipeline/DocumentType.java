package br.com.nfse.renomeador.pipeline;

import java.nio.file.Path;

public enum DocumentType {
    PDF("PDF", ".pdf"),
    XML("XML", ".xml");

    private final String folderName;
    private final String extension;

    DocumentType(String folderName, String extension) {
        this.folderName = folderName;
        this.extension = extension;
    }

    public String folderName() {
        return folderName;
    }

    public String extension() {
        return extension;
    }

    public Path folderUnder(Path root) {
        return root.resolve(folderName);
    }

    public static DocumentType from(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (fileName.endsWith(".xml")) {
            return XML;
        }
        return PDF;
    }
}
