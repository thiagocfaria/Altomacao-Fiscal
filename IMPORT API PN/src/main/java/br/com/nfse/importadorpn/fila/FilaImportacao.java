package br.com.nfse.importadorpn.fila;

import br.com.nfse.importadorpn.ledger.EstadoDocumento;
import br.com.nfse.importadorpn.ledger.RegistroImportacao;
import br.com.nfse.importadorpn.ledger.RepositorioImportacao;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class FilaImportacao {
    private final RepositorioImportacao repositorio;

    public FilaImportacao(RepositorioImportacao repositorio) {
        this.repositorio = repositorio;
    }

    public List<ItemFila> montar(List<NotaDescoberta> descobertas) throws IOException {
        List<ItemFila> fila = new ArrayList<>();
        Set<String> vistosNaRodada = new HashSet<>();
        for (NotaDescoberta nota : descobertas) {
            String chaveRodada = nota.cnpj() + "|" + nota.nsu() + "|" + nota.chave();
            if (!vistosNaRodada.add(chaveRodada)) {
                continue;
            }
            Optional<RegistroImportacao> existente = repositorio.buscar(nota.cnpj(), nota.nsu(), nota.chave());
            if (existente.map(RegistroImportacao::concluida).orElse(false)) {
                continue;
            }
            AcaoFila acao = existente
                    .filter(r -> r.estadoXml() == EstadoDocumento.CONCLUIDO)
                    .map(r -> AcaoFila.COMPLEMENTAR_PDF_E_DMS)
                    .orElse(AcaoFila.IMPORTAR_XML_E_PDF);
            fila.add(new ItemFila(nota.empresa(), nota.cnpj(), nota.nsu(), nota.chave(), acao));
        }
        return List.copyOf(fila);
    }
}
