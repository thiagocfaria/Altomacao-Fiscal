package br.com.nfse.importadorpn.agenda;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ControleJanelaExecutada {
    private final Path agendaDir;

    public ControleJanelaExecutada(Path agendaDir) {
        this.agendaDir = agendaDir;
    }

    public synchronized boolean jaExecutada(JanelaExecucao janela) throws IOException {
        return carregar(janela.mes()).contains(janela.identificador());
    }

    public synchronized void marcarExecutada(JanelaExecucao janela) throws IOException {
        Set<String> executadas = carregar(janela.mes());
        if (executadas.add(janela.identificador())) {
            gravar(janela.mes(), executadas);
        }
    }

    private Set<String> carregar(YearMonth mes) throws IOException {
        Path arquivo = arquivo(mes);
        Set<String> linhas = new LinkedHashSet<>();
        if (!Files.exists(arquivo)) {
            return linhas;
        }
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            if (!linha.isBlank() && !linha.startsWith("janela")) {
                linhas.add(linha.strip());
            }
        }
        return linhas;
    }

    private void gravar(YearMonth mes, Set<String> executadas) throws IOException {
        Files.createDirectories(agendaDir);
        Path arquivo = arquivo(mes);
        Path temp = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
        StringBuilder content = new StringBuilder("janela\n");
        for (String linha : executadas) {
            content.append(linha).append('\n');
        }
        Files.writeString(temp, content.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, arquivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, arquivo, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path arquivo(YearMonth mes) {
        return agendaDir.resolve(mes + ".tsv");
    }
}
