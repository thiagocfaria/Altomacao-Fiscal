package br.com.nfse.renomeador.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsScriptsTest {
    private static final Path SCRIPTS = Path.of("scripts/windows");

    @Test
    void operationalScriptsExplainHowToBuildJarWhenItIsMissing() throws Exception {
        for (String script : operationalScripts()) {
            String content = Files.readString(SCRIPTS.resolve(script));

            assertThat(content)
                    .contains("if not exist \"%JAR%\"")
                    .contains("compilar.bat");
        }
    }

    @Test
    void compileScriptBuildsTheDistributionJar() throws Exception {
        String content = Files.readString(SCRIPTS.resolve("compilar.bat"));

        assertThat(content)
                .contains("mvn clean package")
                .contains("renomeador-nfse-0.1.0-SNAPSHOT.jar");
    }

    private static String[] operationalScripts() {
        return new String[]{
                "preparar-planilha.bat",
                "importar-planilha.bat",
                "validar-config.bat",
                "rodar-batch-homologacao.bat",
                "rodar-batch-producao.bat",
                "rodar-watch.bat"
        };
    }
}
