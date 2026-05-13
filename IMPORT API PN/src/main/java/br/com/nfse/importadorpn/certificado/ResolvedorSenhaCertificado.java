package br.com.nfse.importadorpn.certificado;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ResolvedorSenhaCertificado {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ENV = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");

    private final Map<String, String> ambiente;

    public ResolvedorSenhaCertificado() {
        this(System.getenv());
    }

    public ResolvedorSenhaCertificado(Map<String, String> ambiente) {
        this.ambiente = Map.copyOf(ambiente);
    }

    public ResultadoSenhaCertificado resolver(String alias) {
        return resolver(alias, null);
    }

    public ResultadoSenhaCertificado resolver(String alias, String senhaPlanilha) {
        String variavel = variavelPara(alias);
        String senha = ambiente.get(variavel);
        if (senha != null && !senha.isBlank()) {
            return ResultadoSenhaCertificado.encontrada(senha, variavel);
        }
        if (senhaPlanilha != null && !senhaPlanilha.isBlank()) {
            return ResultadoSenhaCertificado.encontrada(senhaPlanilha, "PLANILHA_FISCAL");
        }
        return ResultadoSenhaCertificado.ausente(variavel);
    }

    public static String variavelPara(String alias) {
        String normalized = Normalizer.normalize(alias == null ? "" : alias, Normalizer.Form.NFD)
                .transform(DIACRITICS::matcher).replaceAll("")
                .toUpperCase(Locale.ROOT)
                .transform(NON_ENV::matcher).replaceAll("_")
                .transform(EDGE_UNDERSCORES::matcher).replaceAll("");
        if (normalized.isBlank()) {
            normalized = "SEM_ALIAS";
        }
        return "IMPORT_API_PN_CERT_" + normalized;
    }
}
