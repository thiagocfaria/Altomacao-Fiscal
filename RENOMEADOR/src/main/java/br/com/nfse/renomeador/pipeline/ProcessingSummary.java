package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.processing.ProcessingStatus;

import java.util.EnumMap;
import java.util.Map;

public final class ProcessingSummary {
    private int total;
    private int skipped;
    private int errors;
    private int repaired;
    private final Map<ProcessingStatus, Integer> byStatus = new EnumMap<>(ProcessingStatus.class);

    public void record(ProcessingStatus status) {
        total++;
        byStatus.merge(status, 1, Integer::sum);
    }

    public void recordSkipped() {
        skipped++;
    }

    public void recordError() {
        total++;
        errors++;
    }

    public void recordRepaired() {
        repaired++;
    }

    public int total() {
        return total;
    }

    public int skipped() {
        return skipped;
    }

    public int errors() {
        return errors;
    }

    public int count(ProcessingStatus status) {
        return byStatus.getOrDefault(status, 0);
    }

    public int repaired() {
        return repaired;
    }
}
