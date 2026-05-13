$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
$Painel = Join-Path $Root "painel.py"
$env:ALTOMACAO_ROOT = $Root

function Resolve-PythonCommand {
    foreach ($Name in @("python3", "py", "python")) {
        $Command = Get-Command $Name -ErrorAction SilentlyContinue
        if ($null -ne $Command) {
            return $Command
        }
    }
    throw "Python nao encontrado. Instale Python 3 e tente novamente."
}

$Python = Resolve-PythonCommand

if ($Python.Name -eq "py.exe" -or $Python.Name -eq "py") {
    & $Python.Source -3 $Painel
} else {
    & $Python.Source $Painel
}

if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
