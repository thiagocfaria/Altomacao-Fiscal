package br.com.nfse.renomeador;

import br.com.nfse.renomeador.layout.LayoutDetector;
import br.com.nfse.renomeador.layout.LayoutType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LayoutDetectorTest {
    private final LayoutDetector detector = new LayoutDetector();

    @Test
    void detectsPortalNacionalLayout() {
        String text = """
            DANFSe v1.0
            Documento Auxiliar da NFS-e
            Numero da DPS
            TOMADOR DO SERVICO
            """;

        assertThat(detector.detect(text)).isEqualTo(LayoutType.PORTAL_NACIONAL);
    }

    @Test
    void detectsAbrasfIssnetLayout() {
        String text = """
            NFS-e Nota Fiscal de Servico Eletronica
            Cod. de Autenticidade
            Detalhamento dos Tributos
            Dados do Tomador de Servicos
            """;

        assertThat(detector.detect(text)).isEqualTo(LayoutType.ABRASF_ISSNET);
    }

    @Test
    void detectsMunicipalAbrasfLayoutWithVerificationCode() {
        String text = """
            MUNICIPIO DE GOIANESIA
            Nota Fiscal de Servicos Eletronico - NFS-e
            Codigo verificacao: 7770752034260416
            PRESTADOR DE SERVICOS
            TOMADOR DE SERVICOS
            Valor dos servicos R$ 400,00
            """;

        assertThat(detector.detect(text)).isEqualTo(LayoutType.ABRASF_ISSNET);
    }

    @Test
    void rejectsUnsupportedTextualLayout() {
        String text = "PREFEITURA DO MUNICIPIO DE SAO PAULO NOTA FISCAL ELETRONICA DE SERVICOS";

        assertThat(detector.detect(text)).isEqualTo(LayoutType.UNSUPPORTED);
    }

    @Test
    void marksPdfWithoutEnoughTextAsNoText() {
        assertThat(new LayoutDetector(40).detect("\f  \n")).isEqualTo(LayoutType.NO_TEXT);
    }
}
