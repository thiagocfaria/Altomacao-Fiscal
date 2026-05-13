package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.time.YearMonth;
import java.util.Optional;

public final class RenomeadorConfigPreflight {
    private final RuntimeCompanyPaths companyPaths = new RuntimeCompanyPaths();

    public ResultadoRenomeadorPreflight verificar(Path config, Optional<YearMonth> mes) throws IOException {
        try (ApplicationLock ignored = ApplicationLock.acquire(config);
             WatchService watchService = FileSystems.getDefault().newWatchService()) {
            CompanyRouteDirectory routes = companyPaths.loadRoutes(config, Optional.empty(), mes);
            int registered = 0;
            for (ResolvedCompanyPath path : routes.monitoredPaths()) {
                Path input = path.root().resolve(path.company().folders().input());
                input.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                registered++;
            }
            testarBackend(routes.backendRoot());
            return new ResultadoRenomeadorPreflight(routes.monitoredPaths().size(), registered, routes.backendRoot());
        }
    }

    private static void testarBackend(Path backendRoot) throws IOException {
        Files.createDirectories(backendRoot);
        Path teste = null;
        try {
            teste = Files.createTempFile(backendRoot, ".preflight-", ".tmp");
        } finally {
            if (teste != null) {
                Files.deleteIfExists(teste);
            }
        }
    }
}
