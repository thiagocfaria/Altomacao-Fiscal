package br.com.nfse.importadorpn.ledger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RepositorioImportacao {
    private static final String HEADER = "cnpj\tnsu\tchave\tempresa\testado_xml\testado_pdf\testado_dms"
            + "\ttentativas\tultimo_erro\tcaminho_rest_final\tcaminho_dms_final\tatualizado_em";

    private final Path arquivo;

    public RepositorioImportacao(Path ledgerDir, YearMonth mes) {
        this.arquivo = ledgerDir.resolve(mes + ".tsv");
    }

    public synchronized void salvar(RegistroImportacao registro) throws IOException {
        Map<String, RegistroImportacao> registros = carregarPorChave();
        registros.put(chaveInterna(registro.cnpj(), registro.nsu(), registro.chave()), registro);
        gravar(registros.values());
    }

    public synchronized Optional<RegistroImportacao> buscar(String cnpj, String nsu, String chave) throws IOException {
        return Optional.ofNullable(carregarPorChave().get(chaveInterna(cnpj, nsu, chave)));
    }

    public synchronized boolean jaConcluida(String cnpj, String nsu, String chave) throws IOException {
        return buscar(cnpj, nsu, chave).map(RegistroImportacao::concluida).orElse(false);
    }

    public synchronized List<RegistroImportacao> listar() throws IOException {
        return List.copyOf(carregarPorChave().values());
    }

    private Map<String, RegistroImportacao> carregarPorChave() throws IOException {
        Map<String, RegistroImportacao> registros = new LinkedHashMap<>();
        if (!Files.exists(arquivo)) {
            return registros;
        }
        List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        for (int i = 1; i < linhas.size(); i++) {
            if (linhas.get(i).isBlank()) {
                continue;
            }
            RegistroImportacao registro = parse(linhas.get(i));
            registros.put(chaveInterna(registro.cnpj(), registro.nsu(), registro.chave()), registro);
        }
        return registros;
    }

    private void gravar(Iterable<RegistroImportacao> registros) throws IOException {
        Files.createDirectories(arquivo.getParent());
        List<String> linhas = new ArrayList<>();
        linhas.add(HEADER);
        for (RegistroImportacao registro : registros) {
            linhas.add(format(registro));
        }
        Path temp = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
        Files.write(temp, linhas, StandardCharsets.UTF_8);
        try {
            Files.move(temp, arquivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, arquivo, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String chaveInterna(String cnpj, String nsu, String chave) {
        return safe(cnpj) + "|" + safe(nsu) + "|" + safe(chave);
    }

    private static String format(RegistroImportacao registro) {
        return String.join("\t",
                escape(registro.cnpj()),
                escape(registro.nsu()),
                escape(registro.chave()),
                escape(registro.empresa()),
                registro.estadoXml().name(),
                registro.estadoPdf().name(),
                registro.estadoDms().name(),
                Integer.toString(registro.tentativas()),
                escape(registro.ultimoErro()),
                escape(registro.caminhoRestFinal()),
                escape(registro.caminhoDmsFinal()),
                registro.atualizadoEm().toString());
    }

    private static RegistroImportacao parse(String linha) {
        String[] parts = linha.split("\t", -1);
        if (parts.length != 12) {
            throw new IllegalArgumentException("Linha de ledger invalida");
        }
        return new RegistroImportacao(
                unescape(parts[0]),
                unescape(parts[1]),
                unescape(parts[2]),
                unescape(parts[3]),
                EstadoDocumento.valueOf(parts[4]),
                EstadoDocumento.valueOf(parts[5]),
                EstadoDocumento.valueOf(parts[6]),
                Integer.parseInt(parts[7]),
                unescape(parts[8]),
                unescape(parts[9]),
                unescape(parts[10]),
                Instant.parse(parts[11]));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                result.append(switch (current) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> current;
                });
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else {
                result.append(current);
            }
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }
}
