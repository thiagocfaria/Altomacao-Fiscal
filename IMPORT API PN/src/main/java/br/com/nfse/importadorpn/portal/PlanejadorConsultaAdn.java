package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Objects;

public final class PlanejadorConsultaAdn {
    private final ClienteAdn clienteAdn;

    public PlanejadorConsultaAdn(ClienteAdn clienteAdn) {
        this.clienteAdn = Objects.requireNonNull(clienteAdn, "clienteAdn");
    }

    public List<ConsultaAdnPlanejada> planejarDfePorNsu(List<EmpresaImportacao> empresas, String nsu) {
        return empresas.stream()
                .map(empresa -> {
                    HttpRequest request = clienteAdn.consultarDfePorNsu(nsu, empresa.cnpj());
                    return new ConsultaAdnPlanejada(empresa.nome(), empresa.cnpj(), request.method(), request.uri());
                })
                .toList();
    }
}
