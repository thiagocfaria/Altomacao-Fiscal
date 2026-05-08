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

    @Test
    void prepareSpreadsheetScriptRepairsWorkbookLevelDoubleClickMacro() throws Exception {
        String prepareScript = Files.readString(SCRIPTS.resolve("preparar-planilha.bat"));
        String macroRepairScript = Files.readString(SCRIPTS.resolve("corrigir-macro-planilha.vbs"));

        assertThat(prepareScript)
                .contains("corrigir-macro-planilha.vbs")
                .contains("cscript //nologo");
        assertThat(macroRepairScript)
                .contains("Workbook_SheetBeforeDoubleClick")
                .contains("Left(UCase(Sh.Name), 9) <> \"\"CADASTRO \"\"")
                .contains("Target.Column < 17 Or Target.Column > 20")
                .contains("msoFileDialogFolderPicker");
    }

    @Test
    void productionBatchUsesYamlValidatedByScriptWithoutSecondSpreadsheetRefresh() throws Exception {
        String content = Files.readString(SCRIPTS.resolve("rodar-batch-producao.bat"));

        assertThat(content)
                .contains("config import-excel")
                .contains("config check")
                .contains("--sem-atualizar-planilha");
    }

    @Test
    void releaseScriptRunsDependencyTreeVerifyPackageAndJarHelp() throws Exception {
        String content = Files.readString(SCRIPTS.resolve("verificar-release.bat"));

        assertThat(content)
                .contains("dependency:tree")
                .contains("verify -Pintegration")
                .contains("package")
                .contains("java -jar \"%JAR%\" --help");
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
