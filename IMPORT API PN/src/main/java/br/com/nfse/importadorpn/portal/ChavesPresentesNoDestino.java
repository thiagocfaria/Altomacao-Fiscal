package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Escaneia as pastas destino do cliente (CAMINHO REST e CAMINHO DMS da planilha)
 * e retorna o conjunto de chaves de acesso ja presentes la.
 *
 * Esta classe e o coracao da REGRA INVARIANTE #1: a decisao de importar/re-importar
 * uma nota olha EXCLUSIVAMENTE para o disco no destino do cliente, NUNCA para indices
 * internos. Se o operador apagar um arquivo, a chave correspondente nao aparece no
 * resultado, e o sistema re-importa.
 *
 * Ver: REGRAS_INVARIANTES.md (REGRA #1)
 */
public final class ChavesPresentesNoDestino {

    /**
     * Padrao que casa uma chave de acesso NFS-e nacional dentro do XML.
     * Aceita varias formas usadas na pratica:
     *   - <chNFSe>50 digitos</chNFSe>
     *   - <chaveAcesso>50 digitos</chaveAcesso>
     *   - Id="NFS50digitos" (atributo do infNFSe, prefixo NFS sem 'e')
     *   - Id="NFSe50digitos" (variante com 'e')
     */
    private static final Pattern CHAVE_PATTERN = Pattern.compile(
            "(?:<chNFSe>|<chaveAcesso>|Id=\"NFSe?)(\\d{50})"
    );

    /**
     * Retorna o conjunto de chaves que estao COMPLETAS no destino do cliente.
     *
     * Uma chave so e considerada "completa" se:
     *   - Tem XML em REST/XML/processados (ou em XML/canceladas)
     *   - Tem PDF correspondente em REST/PDF/processados ou PDF/canceladas
     *     (mesmo basename do XML)
     *   - Tem XML em CAMINHO DMS
     *
     * Se faltar QUALQUER um destes, a chave nao entra no set e o sistema re-importa
     * para repor o que faltou. Garante REGRA INVARIANTE #1 verificando o disco real.
     */
    public Set<String> escanear(EmpresaImportacao empresa, YearMonth mes) throws IOException {
        return escanearEstado(empresa, mes).chavesCompletas();
    }

    public EstadoDestinoNotas escanearEstado(EmpresaImportacao empresa, YearMonth mes) throws IOException {
        Set<String> chavesXmlRest = new HashSet<>();
        Set<String> chavesPdfRest = new HashSet<>();
        Set<String> chavesDms = new HashSet<>();
        boolean restMonitorado = empresa.caminhoRest().isPresent();
        boolean dmsMonitorado = empresa.caminhoDms().isPresent();

        if (empresa.caminhoRest().isPresent()) {
            Path rest = empresa.caminhoRest().orElseThrow();
            Path xmlProc = rest.resolve("XML").resolve("processados");
            Path xmlRetido = rest.resolve("XML").resolve("RETIDO");
            Path xmlCanc = rest.resolve("XML").resolve("canceladas");
            Path pdfProc = rest.resolve("PDF").resolve("processados");
            Path pdfRetido = rest.resolve("PDF").resolve("RETIDO");
            Path pdfCanc = rest.resolve("PDF").resolve("canceladas");
            PdfIndex pdfIndex = PdfIndex.from(pdfProc, pdfRetido, pdfCanc);
            scanIfExists(xmlProc, chavesXmlRest);
            scanIfExists(xmlRetido, chavesXmlRest);
            scanXmlComPdfPar(xmlProc, pdfIndex, chavesPdfRest);
            scanXmlComPdfPar(xmlRetido, pdfIndex, chavesPdfRest);
            // Para XMLs em "canceladas" o PDF pode ou nao existir (notas canceladas
            // muitas vezes nao tem PDF emitido), aceita XML cancelado sem PDF
            scanIfExists(xmlCanc, chavesXmlRest);
            scanIfExists(xmlCanc, chavesPdfRest);
        }

        if (empresa.caminhoDms().isPresent()) {
            scanIfExists(empresa.caminhoDms().orElseThrow(), chavesDms);
        }

        return new EstadoDestinoNotas(chavesXmlRest, chavesPdfRest, chavesDms, restMonitorado, dmsMonitorado);
    }

    /**
     * Para cada XML na pasta, so adiciona a chave ao set se existir um PDF com
     * o mesmo basename em alguma das pastas de PDFs fornecidas. Isso impede que
     * notas com XML mas SEM PDF sejam consideradas "presentes" - o reconciliar
     * vai detectar e re-baixar o PDF que falta.
     */
    private static void scanXmlComPdfPar(Path xmlDir, PdfIndex pdfIndex, Set<String> chaves) {
        if (!Files.isDirectory(xmlDir)) return;
        try (Stream<Path> arquivos = Files.list(xmlDir)) {
            arquivos.filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .forEach(xmlPath -> {
                        String basename = xmlPath.getFileName().toString();
                        basename = basename.substring(0, basename.length() - 4); // tira ".xml"
                        if (pdfIndex.existePara(basename)) {
                            extrairChave(xmlPath).ifPresent(chaves::add);
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static void scanIfExists(Path pasta, Set<String> chaves) {
        if (!Files.isDirectory(pasta)) {
            return;
        }
        try (Stream<Path> arquivos = Files.list(pasta)) {
            arquivos.filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .forEach(p -> extrairChave(p).ifPresent(chaves::add));
        } catch (IOException ignored) {
            // pasta inacessivel: trata como vazia, sistema vai re-importar
        }
    }

    private static java.util.Optional<String> extrairChave(Path arquivoXml) {
        try {
            // Lemos apenas os primeiros 8KB - chave aparece bem no comeco do XML
            byte[] bytes = lerInicio(arquivoXml, 8192);
            String inicio = new String(bytes, StandardCharsets.UTF_8);
            Matcher m = CHAVE_PATTERN.matcher(inicio);
            if (m.find()) {
                return java.util.Optional.of(m.group(1));
            }
        } catch (IOException ignored) {
            // ignora arquivo ilegivel
        }
        return java.util.Optional.empty();
    }

    private static byte[] lerInicio(Path arquivo, int maximoBytes) throws IOException {
        try (var canal = Files.newByteChannel(arquivo)) {
            int alvo = (int) Math.min(canal.size(), maximoBytes);
            byte[] buffer = new byte[alvo];
            java.nio.ByteBuffer wrapper = java.nio.ByteBuffer.wrap(buffer);
            while (wrapper.hasRemaining() && canal.read(wrapper) != -1) {
                // continua lendo
            }
            return buffer;
        }
    }

    private record PdfIndex(Set<String> nomesPdf) {
        static PdfIndex from(Path... pdfDirs) {
            Set<String> nomes = new HashSet<>();
            for (Path pdfDir : pdfDirs) {
                if (!Files.isDirectory(pdfDir)) {
                    continue;
                }
                try (Stream<Path> arquivos = Files.list(pdfDir)) {
                    arquivos.map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                            .filter(nome -> nome.endsWith(".pdf"))
                            .forEach(nomes::add);
                } catch (IOException ignored) {
                    // pasta inacessivel: trata como vazia, sistema vai re-importar
                }
            }
            return new PdfIndex(Set.copyOf(nomes));
        }

        boolean existePara(String basename) {
            String base = basename.toLowerCase(Locale.ROOT);
            if (nomesPdf.contains(base + ".pdf")) {
                return true;
            }
            String prefixoDuplicado = base + "_";
            return nomesPdf.stream().anyMatch(nome -> nome.startsWith(prefixoDuplicado));
        }
    }
}
