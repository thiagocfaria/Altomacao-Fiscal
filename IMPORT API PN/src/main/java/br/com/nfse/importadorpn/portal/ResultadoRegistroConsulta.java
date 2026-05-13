package br.com.nfse.importadorpn.portal;

public record ResultadoRegistroConsulta(
        boolean publicado,
        boolean falhaRegistrada,
        String caminhoPublicado,
        String caminhoTecnico,
        int documentosPublicados
) {
    public ResultadoRegistroConsulta(boolean publicado, boolean falhaRegistrada,
            String caminhoPublicado, String caminhoTecnico) {
        this(publicado, falhaRegistrada, caminhoPublicado, caminhoTecnico, publicado ? 1 : 0);
    }
}
