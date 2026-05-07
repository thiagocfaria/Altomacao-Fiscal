package br.com.nfse.renomeador.pipeline;

import java.nio.file.Path;

public record DestinationResult(Path destination, boolean sourcePreserved) {
}
