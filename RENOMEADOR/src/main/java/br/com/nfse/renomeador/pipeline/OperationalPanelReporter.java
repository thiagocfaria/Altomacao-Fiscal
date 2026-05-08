package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.processing.ProcessingStatus;
import br.com.nfse.renomeador.text.TsvCodec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

final class OperationalPanelReporter {
    private static final String FILE_NAME = "painel-operacional.tsv";

    void record(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath, ProcessingSummary summary)
            throws IOException {
        Path panel = routes.backendRoot().resolve(FILE_NAME);
        Files.createDirectories(panel.getParent());
        ensureHeader(panel);
        appendLine(panel, lineFor(companyPath, summary));
    }

    private static void ensureHeader(Path panel) throws IOException {
        if (Files.exists(panel) && Files.size(panel) > 0) {
            return;
        }
        appendLine(panel, "instante\tstatus\tempresa\ttotal\tok\trevisar\tcanceladas\tduplicadas\tignorados\terros\tacao"
                + System.lineSeparator());
    }

    private static String lineFor(ResolvedCompanyPath companyPath, ProcessingSummary summary) {
        String status = summary.needsAttention() ? "ATENCAO" : "OK";
        String action = summary.needsAttention() ? "VERIFICAR_REVISAR_E_LOG" : "NENHUMA";
        return TsvCodec.join(
                Instant.now().toString(),
                status,
                companyPath.company().id(),
                "total=" + summary.total(),
                "ok=" + summary.count(ProcessingStatus.OK),
                "revisar=" + summary.reviewCount(),
                "canceladas=" + summary.count(ProcessingStatus.CANCELLED),
                "duplicadas=" + summary.count(ProcessingStatus.DUPLICATE),
                "ignorados=" + summary.skipped(),
                "erros=" + summary.errors(),
                "acao=" + action
        ) + System.lineSeparator();
    }

    private static void appendLine(Path path, String line) throws IOException {
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
        }
    }

}
