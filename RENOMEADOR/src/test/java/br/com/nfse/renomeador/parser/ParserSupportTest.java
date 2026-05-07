package br.com.nfse.renomeador.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParserSupportTest {
    @Test
    void sectionUsesOriginalLineBoundariesWhenTextBeforeMarkerHasCombiningAccent() {
        String text = "a\u0301\n"
                + "Dados do Prestador\n"
                + "Empresa Árvore\n"
                + "Identificacao da Nota Fiscal\n"
                + "fora";

        String section = ParserSupport.section(text, "Dados do Prestador", "Identificacao da Nota Fiscal");

        assertThat(section).isEqualTo("Dados do Prestador\nEmpresa Árvore\n");
    }
}
