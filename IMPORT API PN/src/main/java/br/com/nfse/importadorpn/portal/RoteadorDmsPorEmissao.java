package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;

public final class RoteadorDmsPorEmissao implements RoteadorDms {
    public RoteadorDmsPorEmissao(CadastroImportacao cadastro, int anoReferencia) {
        // Mantem a assinatura usada pelo reconciliador/pre-voo. A rota agora pertence
        // sempre a empresa da linha ativa; o cadastro completo nao cria rotas herdadas.
    }

    @Override
    public Optional<DestinoDmsResolvido> resolver(EmpresaImportacao empresa, DocumentoDfeExtraido documento,
            YearMonth mesComando) {
        DadosDocumentoDfe dados = DadosDocumentoDfe.fromXml(documento.xml());
        if (dados.mesEmissao().isEmpty() || !dados.mesEmissao().orElseThrow().equals(mesComando)
                || !dados.pertenceAoCnpj(empresa.cnpj()) || empresa.caminhoDms().isEmpty()) {
            return Optional.empty();
        }
        Path caminhoDms = empresa.caminhoDms().orElseThrow();
        return Optional.of(new DestinoDmsResolvido(caminhoDms, empresa.cnpj(), mesComando));
    }
}
