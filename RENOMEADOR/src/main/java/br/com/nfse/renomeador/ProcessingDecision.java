package br.com.nfse.renomeador;

import br.com.nfse.renomeador.processing.ProcessingStatus;

public record ProcessingDecision(ProcessingStatus status, boolean reviewRequired, String reason) {
}
