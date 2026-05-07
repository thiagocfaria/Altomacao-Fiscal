package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.ResolvedCompanyPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class InputScanner {
    public List<InputCandidate> scan(List<ResolvedCompanyPath> companyPaths) throws IOException {
        List<InputCandidate> candidates = new java.util.ArrayList<>();
        for (ResolvedCompanyPath companyPath : companyPaths) {
            Path inputDirectory = PathsForCompany.input(companyPath);
            if (!Files.isDirectory(inputDirectory)) {
                continue;
            }
            try (var stream = Files.list(inputDirectory)) {
                stream.filter(Files::isRegularFile)
                        .filter(InputScanner::isPdf)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(path -> new InputCandidate(companyPath, path))
                        .forEach(candidates::add);
            }
        }
        return List.copyOf(candidates);
    }

    private static boolean isPdf(Path path) {
        return path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }
}
