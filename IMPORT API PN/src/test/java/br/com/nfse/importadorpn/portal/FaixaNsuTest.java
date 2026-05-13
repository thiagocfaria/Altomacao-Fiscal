package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FaixaNsuTest {
    @Test
    void aceitaFaixaPequenaEOrdenada() {
        FaixaNsu faixa = new FaixaNsu(1, 20);

        assertThat(faixa.valores()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
    }

    @Test
    void bloqueiaFaixaGrandeDemaisParaNaoAgredirPortal() {
        assertThatThrownBy(() -> new FaixaNsu(1, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite maximo");
    }

    @Test
    void bloqueiaFimMenorQueInicio() {
        assertThatThrownBy(() -> new FaixaNsu(20, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fim deve ser maior");
    }
}
