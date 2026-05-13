#!/usr/bin/env python3
"""
Painel simples de operacao do sistema de automacao fiscal.

Controles principais:
  - VERIFICAR TUDO: valida cadastro, certificados e mostra o que o sistema faria.
  - LIGAR/DESLIGAR SISTEMA: inicia ou encerra RENOMEADOR + loop de reconciliacao.
  - MES DE ATUACAO: define o mes passado para o importador no reconciliar.
"""
from __future__ import annotations
from dataclasses import dataclass
import json
import os
import re
import signal
import subprocess
import threading
import time
import tkinter as tk
from datetime import datetime, timezone
from pathlib import Path
from tkinter import scrolledtext, messagebox

ROOT = Path(os.environ.get("ALTOMACAO_ROOT", Path(__file__).resolve().parent)).resolve()
JAR_IMPORT = ROOT / "IMPORT API PN/target/importador-api-pn-0.1.0-SNAPSHOT.jar"
JAR_RENOMEADOR = ROOT / "RENOMEADOR/target/renomeador-nfse-0.1.0-SNAPSHOT.jar"
PLANILHA = ROOT / "PLANILHA_FISCAL.xlsm"
BACKEND = ROOT / "IMPORT API PN/backend"
CONFIG_RENOMEADOR = ROOT / "RENOMEADOR/operacao/empresas.yaml"
HEALTH_RENOMEADOR = ROOT / "RENOMEADOR/operacao/backend/health/watch-status.json"
INTERVALO_SEGUNDOS = int(os.environ.get("ALTOMACAO_INTERVALO_SEGUNDOS", "60"))
AMBIENTE = os.environ.get("ALTOMACAO_AMBIENTE", "PRODUCAO")
NSU_INICIAL = os.environ.get("ALTOMACAO_NSU_INICIAL", "1")
MAX_LOTES_RECONCILIACAO = os.environ.get("ALTOMACAO_MAX_LOTES_RECONCILIACAO", "500")
PORTAL_TENTATIVAS = os.environ.get("ALTOMACAO_PORTAL_TENTATIVAS", "5")
PORTAL_RETRY_BASE_MS = os.environ.get("ALTOMACAO_PORTAL_RETRY_BASE_MS", "2000")
HEALTH_MAX_IDADE_SEGUNDOS = int(
    os.environ.get("ALTOMACAO_HEALTH_MAX_IDADE_SEGUNDOS", str(max(120, INTERVALO_SEGUNDOS * 3)))
)
TITULO_SISTEMA = "Sistema Prótons"
SUBTITULO_SISTEMA = "Automacao Fiscal NFS-e"
ASSINATURA_DEV = "DEV Thiago Caetano Faria"
MENTORIA_PROJETO = "Mentoria: Fernando, Wilderson e Ana Carolina"
MES_RE = re.compile(r"^\d{4}-(0[1-9]|1[0-2])$")


@dataclass(frozen=True)
class ParametrosVerificacao:
    mes: str
    ambiente: str
    nsu: str
    max_lotes: str
    portal_tentativas: str
    portal_retry_base_ms: str


@dataclass(frozen=True)
class HealthRenomeador:
    status: str
    total: int
    revisar: int
    ignorados: int
    erros: int
    mensagem: str
    ultimo_pulso: datetime | None = None

    @property
    def sem_pendencia_ativa(self) -> bool:
        return self.total == 0 and self.revisar == 0 and self.erros == 0

    def pulso_atual(self, agora: datetime, max_idade_segundos: int) -> bool:
        if self.ultimo_pulso is None:
            return False
        referencia = _normalizar_datetime(agora)
        ultimo = _normalizar_datetime(self.ultimo_pulso)
        idade = (referencia - ultimo).total_seconds()
        return 0 <= idade <= max_idade_segundos


@dataclass(frozen=True)
class ResumoPosReconciliacao:
    mensagem: str
    tag: str
    sem_pendencia_ativa: bool
    tudo_conferido: bool


def mes_atuacao(automatico: bool, mes_manual: str, agora: datetime | None = None) -> str:
    """Resolve o mes que o reconciliador deve usar no formato AAAA-MM."""
    referencia = agora or datetime.now()
    if automatico:
        return referencia.strftime("%Y-%m")
    mes = mes_manual.strip()
    if not MES_RE.fullmatch(mes):
        raise ValueError("Mes de atuacao deve estar no formato AAAA-MM.")
    return mes


def comando_verificar_tudo(mes: str) -> list[str]:
    return [
        "java", "-jar", JAR_IMPORT, "verificar-tudo",
        "--planilha", PLANILHA, "--backend", BACKEND,
        "--ambiente", AMBIENTE, "--nsu", NSU_INICIAL,
        "--max-lotes", MAX_LOTES_RECONCILIACAO,
        "--tentativas-portal", PORTAL_TENTATIVAS,
        "--retry-portal-ms", PORTAL_RETRY_BASE_MS,
        "--mes", mes,
    ]


def comando_renomeador_import_excel() -> list[str]:
    return [
        "java", "-jar", JAR_RENOMEADOR, "config", "import-excel",
        "--planilha", PLANILHA, "--saida", CONFIG_RENOMEADOR,
        "--sobrescrever",
    ]


def comando_renomeador_preflight(mes: str) -> list[str]:
    return [
        "java", "-jar", JAR_RENOMEADOR, "config", "preflight",
        "--config", CONFIG_RENOMEADOR,
        "--mes", mes,
    ]


def comando_reconciliar(mes: str) -> list[str]:
    return [
        "java", "-jar", JAR_IMPORT, "reconciliar",
        "--planilha", PLANILHA, "--backend", BACKEND,
        "--ambiente", AMBIENTE, "--nsu", NSU_INICIAL,
        "--max-lotes", MAX_LOTES_RECONCILIACAO,
        "--tentativas-portal", PORTAL_TENTATIVAS,
        "--retry-portal-ms", PORTAL_RETRY_BASE_MS,
        "--mes", mes,
    ]


def jars_ausentes(
    jar_import: Path = JAR_IMPORT,
    jar_renomeador: Path = JAR_RENOMEADOR,
) -> list[Path]:
    return [jar for jar in (jar_import, jar_renomeador) if not jar.is_file()]


def parametros_verificacao(mes: str) -> ParametrosVerificacao:
    return ParametrosVerificacao(
        mes, AMBIENTE, NSU_INICIAL, MAX_LOTES_RECONCILIACAO,
        PORTAL_TENTATIVAS, PORTAL_RETRY_BASE_MS,
    )


def ler_health_renomeador(path: Path = HEALTH_RENOMEADOR) -> HealthRenomeador | None:
    try:
        dados = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None

    def inteiro(campo: str) -> int:
        try:
            return int(dados.get(campo, 0))
        except (TypeError, ValueError):
            return 0

    return HealthRenomeador(
        status=str(dados.get("status", "")),
        total=inteiro("total"),
        revisar=inteiro("revisar"),
        ignorados=inteiro("ignorados"),
        erros=inteiro("erros"),
        mensagem=str(dados.get("mensagem", "")),
        ultimo_pulso=_parse_datetime_iso(dados.get("ultimoPulso")),
    )


def _parse_datetime_iso(valor: object) -> datetime | None:
    if not isinstance(valor, str) or not valor.strip():
        return None
    texto = valor.strip()
    if texto.endswith("Z"):
        texto = texto[:-1] + "+00:00"
    try:
        return datetime.fromisoformat(texto)
    except ValueError:
        return None


def _normalizar_datetime(valor: datetime) -> datetime:
    if valor.tzinfo is None:
        return valor.replace(tzinfo=timezone.utc)
    return valor.astimezone(timezone.utc)


def documentos_reimportados_saida(saida: str) -> int:
    finais = [
        int(match.group(1))
        for match in re.finditer(r"^Documentos re-importados:\s*(\d+)\s*$", saida, re.MULTILINE)
    ]
    if finais:
        return finais[-1]
    return sum(
        int(match.group(1))
        for match in re.finditer(r"^\s*Re-importados:\s*(\d+)\s*$", saida, re.MULTILINE)
    )


def _sufixo_proximo_ciclo(intervalo_segundos: int, proximo_ciclo: bool) -> str:
    if not proximo_ciclo:
        return ""
    return f" Proxima conferencia em {max(1, intervalo_segundos)}s."


def resumo_pos_reconciliacao(
    returncode: int,
    saida: str,
    health: HealthRenomeador | None,
    intervalo_segundos: int,
    rodadas_sem_pendencia: int,
    proximo_ciclo: bool,
    agora: datetime | None = None,
    health_max_idade_segundos: int = HEALTH_MAX_IDADE_SEGUNDOS,
) -> ResumoPosReconciliacao:
    sufixo = _sufixo_proximo_ciclo(intervalo_segundos, proximo_ciclo)
    if returncode != 0:
        return ResumoPosReconciliacao(
            "Reconciliacao terminou com erro; veja as linhas acima antes de confiar na rodada." + sufixo,
            "erro",
            False,
            False,
        )
    if "ERRO_EXTERNO" in saida or "ATENCAO: max-lotes" in saida or "Status: ATENCAO" in saida:
        return ResumoPosReconciliacao(
            "Reconciliacao terminou com aviso; veja Portal/max-lotes/configuracao antes de declarar tudo conferido." + sufixo,
            "erro",
            False,
            False,
        )

    reimportados = documentos_reimportados_saida(saida)
    if reimportados > 0:
        return ResumoPosReconciliacao(
            f"Rodada importou {reimportados} documento(s); aguarde o RENOMEADOR organizar a entrada." + sufixo,
            "info",
            False,
            False,
        )

    if health is None:
        return ResumoPosReconciliacao(
            "Rodada sem novas importacoes, mas nao consegui ler o health do RENOMEADOR." + sufixo,
            "erro",
            False,
            False,
        )

    referencia = _normalizar_datetime(agora or datetime.now(timezone.utc))
    if not health.pulso_atual(referencia, health_max_idade_segundos):
        ultimo = "ausente"
        if health.ultimo_pulso is not None:
            ultimo = _normalizar_datetime(health.ultimo_pulso).isoformat()
        return ResumoPosReconciliacao(
            "Rodada sem novas importacoes, mas o health do RENOMEADOR esta desatualizado "
            f"(ultimo pulso: {ultimo}). Reinicie ou confira o RENOMEADOR antes de declarar tudo conferido."
            + sufixo,
            "erro",
            False,
            False,
        )

    if not health.sem_pendencia_ativa:
        return ResumoPosReconciliacao(
            "Rodada sem novas importacoes, mas o RENOMEADOR ainda tem pendencia: "
            f"total={health.total}, revisar={health.revisar}, erros={health.erros}." + sufixo,
            "erro",
            False,
            False,
        )

    detalhe_ignorados = ""
    if health.ignorados > 0:
        detalhe_ignorados = f" ({health.ignorados} ignorado(s) recentes no watcher; confira log se persistir)"
    if rodadas_sem_pendencia < 2:
        return ResumoPosReconciliacao(
            "Rodada sem novas importacoes e sem pendencia ativa; confirmando na proxima rodada "
            "antes de declarar tudo conferido." + detalhe_ignorados + sufixo,
            "info",
            True,
            False,
        )
    return ResumoPosReconciliacao(
        "Tudo conferido nesta rodada: 0 documentos re-importados e entrada REST sem pendencia ativa."
        + detalhe_ignorados + sufixo,
        "ok",
        True,
        True,
    )


def verificacao_continua_valida(
    verificada: ParametrosVerificacao | None,
    mes: str,
    ambiente: str = AMBIENTE,
    nsu: str = NSU_INICIAL,
    max_lotes: str = MAX_LOTES_RECONCILIACAO,
    portal_tentativas: str = PORTAL_TENTATIVAS,
    portal_retry_base_ms: str = PORTAL_RETRY_BASE_MS,
) -> bool:
    return verificada == ParametrosVerificacao(
        mes, ambiente, nsu, max_lotes, portal_tentativas, portal_retry_base_ms,
    )


def classificar_resultado_verificacao(returncodes: list[int]) -> str:
    if any(rc == 4 or rc < 0 for rc in returncodes):
        return "ERRO_EXTERNO"
    if len(returncodes) > 1 and any(rc != 0 for rc in returncodes[1:]):
        return "BLOQUEADO"
    if any(rc == 3 for rc in returncodes):
        return "BLOQUEADO"
    if any(rc == 2 for rc in returncodes):
        return "BLOQUEADO"
    if any(rc == 1 for rc in returncodes):
        return "ATENCAO"
    return "OK"


def tag_linha_log(linha: str) -> str:
    marcadores_erro = (
        "ERRO", "ERROR", "Falha", "BLOQUEADO", "ERRO_EXTERNO", "PODE LIGAR? NAO"
    )
    marcadores_atencao = ("ATENCAO", "PODE LIGAR? SIM, COM ATENCAO")
    if any(marcador in linha for marcador in marcadores_erro):
        return "erro"
    if any(marcador in linha for marcador in marcadores_atencao):
        return "erro"
    if "OK" in linha or "Status: OK" in linha:
        return "ok"
    return ""


class Painel:
    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title(TITULO_SISTEMA)
        self.root.geometry("900x600")
        self.root.configure(bg="#1a1a2e")

        self.processos: list[subprocess.Popen] = []
        self.ligado = False
        self.thread_loop: threading.Thread | None = None
        self.mes_var = tk.StringVar(value=mes_atuacao(True, ""))
        self.automatico_var = tk.BooleanVar(value=True)
        self.parametros_verificados: ParametrosVerificacao | None = None
        self.nivel_verificacao: str | None = None
        self.rodadas_sem_pendencia = 0

        self._construir_ui()
        self._registrar_fechamento()

    # ---------- UI ----------
    def _construir_ui(self) -> None:
        topo = tk.Frame(self.root, bg="#1a1a2e")
        topo.pack(fill=tk.X, padx=20, pady=15)

        marca = tk.Frame(topo, bg="#1a1a2e")
        marca.pack(side=tk.LEFT)

        tk.Label(
            marca, text=TITULO_SISTEMA,
            font=("Helvetica", 27, "bold"), fg="#f8fafc", bg="#1a1a2e",
        ).pack(anchor=tk.W)

        tk.Label(
            marca, text=SUBTITULO_SISTEMA.upper(),
            font=("Helvetica", 10, "bold"), fg="#22d3ee", bg="#1a1a2e",
        ).pack(anchor=tk.W, pady=(1, 0))

        self.lbl_status = tk.Label(
            topo, text="● DESLIGADO",
            font=("Helvetica", 14, "bold"), fg="#888", bg="#1a1a2e",
        )
        self.lbl_status.pack(side=tk.RIGHT)

        controles = tk.Frame(self.root, bg="#1a1a2e")
        controles.pack(fill=tk.X, padx=20, pady=(0, 8))

        tk.Label(
            controles, text="Mes de atuacao",
            font=("Helvetica", 11, "bold"), fg="#d0d0d0", bg="#1a1a2e",
        ).pack(side=tk.LEFT, padx=(0, 8))

        self.btn_mes_anterior = tk.Button(
            controles, text="<", command=lambda: self._ajustar_mes(-1),
            font=("Helvetica", 11, "bold"), width=3, bg="#243447", fg="white",
            activebackground="#334e68", activeforeground="white",
            relief=tk.FLAT, cursor="hand2",
        )
        self.btn_mes_anterior.pack(side=tk.LEFT)

        self.ent_mes = tk.Entry(
            controles, textvariable=self.mes_var, width=9, justify=tk.CENTER,
            font=("Consolas", 13, "bold"), bg="#0a0a0f", fg="#f5f5f5",
            insertbackground="white", relief=tk.FLAT,
        )
        self.ent_mes.pack(side=tk.LEFT, padx=4)

        self.btn_mes_proximo = tk.Button(
            controles, text=">", command=lambda: self._ajustar_mes(1),
            font=("Helvetica", 11, "bold"), width=3, bg="#243447", fg="white",
            activebackground="#334e68", activeforeground="white",
            relief=tk.FLAT, cursor="hand2",
        )
        self.btn_mes_proximo.pack(side=tk.LEFT)

        self.chk_automatico = tk.Checkbutton(
            controles, text="Automatico: mes vigente",
            variable=self.automatico_var, command=self._sincronizar_mes_automatico,
            font=("Helvetica", 11), fg="#d0d0d0", bg="#1a1a2e",
            activeforeground="#ffffff", activebackground="#1a1a2e",
            selectcolor="#0a0a0f", cursor="hand2",
        )
        self.chk_automatico.pack(side=tk.LEFT, padx=16)

        botoes = tk.Frame(self.root, bg="#1a1a2e")
        botoes.pack(fill=tk.X, padx=20, pady=10)

        self.btn_verificar = tk.Button(
            botoes, text="VERIFICAR TUDO", command=self.acao_verificar,
            font=("Helvetica", 14, "bold"), bg="#0f3460", fg="white",
            activebackground="#16498a", activeforeground="white",
            height=2, relief=tk.FLAT, cursor="hand2",
        )
        self.btn_verificar.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=5)

        self.btn_ligar = tk.Button(
            botoes, text="LIGAR SISTEMA", command=self.acao_ligar_desligar,
            font=("Helvetica", 14, "bold"), bg="#16a085", fg="white",
            activebackground="#1abc9c", activeforeground="white",
            height=2, relief=tk.FLAT, cursor="hand2",
        )
        self.btn_ligar.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=5)

        self.area_log = scrolledtext.ScrolledText(
            self.root, font=("Consolas", 10),
            bg="#0a0a0f", fg="#d0d0d0", insertbackground="white",
            relief=tk.FLAT, padx=10, pady=10,
        )
        self.area_log.pack(fill=tk.BOTH, expand=True, padx=20, pady=10)
        self.area_log.tag_config("ok", foreground="#1abc9c")
        self.area_log.tag_config("erro", foreground="#e94560")
        self.area_log.tag_config("info", foreground="#3498db")
        self.area_log.tag_config("titulo", foreground="#f39c12", font=("Consolas", 11, "bold"))

        rodape = tk.Frame(self.root, bg="#1a1a2e")
        rodape.pack(fill=tk.X, padx=20, pady=5)
        credito = tk.Frame(rodape, bg="#1a1a2e")
        credito.pack(side=tk.LEFT)
        tk.Label(
            credito, text=ASSINATURA_DEV,
            font=("Z003", 12, "italic"), fg="#8da4b8", bg="#1a1a2e",
        ).pack(anchor=tk.W)
        tk.Label(
            credito, text=MENTORIA_PROJETO,
            font=("Helvetica", 8), fg="#5f7588", bg="#1a1a2e",
        ).pack(anchor=tk.W)
        tk.Label(
            rodape, text=f"Backend: {BACKEND}",
            font=("Helvetica", 9), fg="#666", bg="#1a1a2e",
        ).pack(side=tk.RIGHT)

        self._log("Painel pronto. Clique em VERIFICAR TUDO para conferir o sistema.\n", "info")
        self._sincronizar_mes_automatico()

    def _sincronizar_mes_automatico(self) -> None:
        if self.automatico_var.get():
            self.mes_var.set(mes_atuacao(True, self.mes_var.get()))
            estado = tk.DISABLED
        else:
            estado = tk.NORMAL
        self.ent_mes.config(state=estado)
        self.btn_mes_anterior.config(state=estado)
        self.btn_mes_proximo.config(state=estado)

    def _ajustar_mes(self, deslocamento: int) -> None:
        try:
            atual = datetime.strptime(mes_atuacao(False, self.mes_var.get()), "%Y-%m")
        except ValueError as exc:
            messagebox.showerror("Mes invalido", str(exc))
            return
        ano = atual.year + ((atual.month - 1 + deslocamento) // 12)
        mes = ((atual.month - 1 + deslocamento) % 12) + 1
        self.mes_var.set(f"{ano:04d}-{mes:02d}")

    def _mes_operacao(self) -> str:
        mes = mes_atuacao(self.automatico_var.get(), self.mes_var.get())
        if self.automatico_var.get():
            self.mes_var.set(mes)
        return mes

    def _registrar_fechamento(self) -> None:
        self.root.protocol("WM_DELETE_WINDOW", self._ao_fechar)

    def _ao_fechar(self) -> None:
        if self.ligado and not messagebox.askyesno(
            "Sistema ligado",
            "O sistema ainda esta ligado. Deseja desligar e sair?",
        ):
            return
        self.acao_desligar()
        self.root.destroy()

    # ---------- Log ----------
    def _log(self, msg: str, tag: str = "") -> None:
        carimbo = datetime.now().strftime("%H:%M:%S")
        linha = f"[{carimbo}] {msg}" if not msg.startswith("\n") else msg
        self.area_log.insert(tk.END, linha, tag)
        self.area_log.see(tk.END)
        self.root.update_idletasks()

    def _titulo(self, texto: str) -> None:
        self._log("\n" + "=" * 60 + "\n", "titulo")
        self._log(f" {texto}\n", "titulo")
        self._log("=" * 60 + "\n\n", "titulo")

    # ---------- Comando ----------
    def _rodar(self, args: list[str], rotulo: str) -> tuple[int, str]:
        self._log(f">>> {rotulo}\n", "info")
        try:
            r = subprocess.run(
                args, capture_output=True, text=True, timeout=600, cwd=ROOT,
            )
            saida = (r.stdout or "") + (("\n" + r.stderr) if r.stderr else "")
            for linha in saida.splitlines():
                self._log(linha + "\n", tag_linha_log(linha))
            return r.returncode, saida
        except subprocess.TimeoutExpired:
            self._log(f"Tempo esgotado em: {rotulo}\n", "erro")
            return -1, "timeout"
        except Exception as e:
            self._log(f"Erro ao rodar {rotulo}: {e}\n", "erro")
            return -1, str(e)

    # ---------- VERIFICAR ----------
    def acao_verificar(self) -> None:
        self.btn_verificar.config(state=tk.DISABLED)
        threading.Thread(target=self._verificar, daemon=True).start()

    def _verificar(self) -> None:
        try:
            self._titulo("VERIFICAR TUDO")
            try:
                mes = self._mes_operacao()
            except ValueError as exc:
                messagebox.showerror("Mes invalido", str(exc))
                self._log(f"Mes de atuacao invalido: {exc}\n", "erro")
                return

            self._log(f"Mes de atuacao: {mes}\n", "info")
            ausentes = jars_ausentes()
            if ausentes:
                for jar in ausentes:
                    self._log(f"NIVEL: BLOQUEADO\nJAR ausente: {jar}\n", "erro")
                self.parametros_verificados = None
                self.nivel_verificacao = "BLOQUEADO"
                return

            self._log("\n--- 1/3 Pre-voo fiel IMPORT API PN ---\n", "info")
            rc1, _ = self._rodar(
                comando_verificar_tudo(mes),
                "verificar-tudo",
            )

            self._log("\n--- 2/3 Atualizando RENOMEADOR para todos os meses ---\n", "info")
            rc4, _ = self._rodar(
                comando_renomeador_import_excel(),
                "renomeador config import-excel",
            )

            self._log("\n--- 3/3 Preflight RENOMEADOR ---\n", "info")
            rc5, _ = self._rodar(
                comando_renomeador_preflight(mes),
                "renomeador config preflight",
            )

            self._log("\n" + "=" * 60 + "\n", "titulo")
            resultado = classificar_resultado_verificacao([rc1, rc4, rc5])
            self.nivel_verificacao = resultado
            if resultado in {"OK", "ATENCAO"}:
                self.parametros_verificados = parametros_verificacao(mes)
            else:
                self.parametros_verificados = None
            if resultado == "OK":
                self._log(" RESULTADO: TUDO OK - sistema pronto para ligar.\n", "ok")
            elif resultado == "ATENCAO":
                self._log(" RESULTADO: ATENCAO - pode ligar apenas aceitando o aviso.\n", "erro")
            elif resultado == "ERRO_EXTERNO":
                self._log(" RESULTADO: ERRO_EXTERNO - Portal/rede/certificado rejeitou a consulta real.\n", "erro")
            else:
                self._log(" RESULTADO: BLOQUEADO - resolva os pontos acima antes de ligar.\n", "erro")
            self._log("=" * 60 + "\n\n", "titulo")
        finally:
            self.btn_verificar.config(state=tk.NORMAL)

    # ---------- LIGAR ----------
    def acao_ligar_desligar(self) -> None:
        if self.ligado:
            self.acao_desligar()
            return
        self.acao_ligar()

    def acao_ligar(self) -> None:
        if self.ligado:
            return
        try:
            mes = self._mes_operacao()
        except ValueError as exc:
            messagebox.showerror("Mes invalido", str(exc))
            self._log(f"Mes de atuacao invalido: {exc}\n", "erro")
            return
        if jars_ausentes():
            self._log("Sistema nao sera ligado: JAR ausente. Rode a build e clique VERIFICAR TUDO novamente.\n", "erro")
            return
        if not verificacao_continua_valida(self.parametros_verificados, mes):
            self._log("Sistema nao sera ligado: rode VERIFICAR TUDO novamente para estes parametros.\n", "erro")
            return
        if self.nivel_verificacao == "ATENCAO" and not messagebox.askyesno(
            "Verificacao com atencao",
            "A ultima verificacao retornou ATENCAO. Deseja ligar mesmo assim?",
        ):
            return
        self.ligado = True
        self.btn_ligar.config(text="DESLIGAR SISTEMA", bg="#7f1d1d", activebackground="#991b1b")
        self.btn_verificar.config(state=tk.DISABLED)
        self.ent_mes.config(state=tk.DISABLED)
        self.btn_mes_anterior.config(state=tk.DISABLED)
        self.btn_mes_proximo.config(state=tk.DISABLED)
        self.chk_automatico.config(state=tk.DISABLED)
        self.lbl_status.config(text="● LIGADO", fg="#1abc9c")

        self._titulo("LIGAR SISTEMA")
        self._log(f"Mes de atuacao: {mes}\n", "info")
        self._log("Atualizando RENOMEADOR com todos os meses da planilha...\n", "info")
        rc_import, _ = self._rodar(
            comando_renomeador_import_excel(),
            "renomeador config import-excel",
        )
        if rc_import != 0:
            self._log("Falha ao atualizar configuracao do RENOMEADOR; sistema nao sera ligado.\n", "erro")
            self.acao_desligar()
            return

        rc_check, _ = self._rodar(
            comando_renomeador_preflight(mes),
            "renomeador config preflight",
        )
        if rc_check != 0:
            self._log("Configuracao do RENOMEADOR invalida; sistema nao sera ligado.\n", "erro")
            self.acao_desligar()
            return

        self._log("Iniciando RENOMEADOR em modo watch (continuo)...\n", "info")

        try:
            proc_watch = subprocess.Popen(
                ["java", "-jar", JAR_RENOMEADOR, "watch",
                 "--config", CONFIG_RENOMEADOR, "--sem-atualizar-planilha"],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                cwd=ROOT, text=True, bufsize=1,
            )
            self.processos.append(proc_watch)
            threading.Thread(
                target=self._streamar_saida,
                args=(proc_watch, "RENOMEADOR"), daemon=True,
            ).start()
            self._log("RENOMEADOR ligado (PID %d).\n" % proc_watch.pid, "ok")
        except Exception as e:
            self._log(f"Falha ao iniciar RENOMEADOR: {e}\n", "erro")

        self._log(f"Iniciando conferidor de janelas a cada {INTERVALO_SEGUNDOS}s...\n", "info")
        self.thread_loop = threading.Thread(target=self._loop_captura, daemon=True)
        self.thread_loop.start()

    def _streamar_saida(self, proc: subprocess.Popen, rotulo: str) -> None:
        if proc.stdout is None:
            return
        for linha in proc.stdout:
            self._log(f"[{rotulo}] {linha}")
        self._log(f"[{rotulo}] processo encerrou.\n", "info")

    def _registrar_pos_reconciliacao(self, rc: int, saida: str, proximo_ciclo: bool) -> None:
        health = ler_health_renomeador()
        preliminar = resumo_pos_reconciliacao(
            rc, saida, health, INTERVALO_SEGUNDOS, 1, proximo_ciclo,
        )
        if preliminar.sem_pendencia_ativa:
            self.rodadas_sem_pendencia += 1
        else:
            self.rodadas_sem_pendencia = 0
        resumo = resumo_pos_reconciliacao(
            rc, saida, health, INTERVALO_SEGUNDOS, self.rodadas_sem_pendencia, proximo_ciclo,
        )
        self._log(resumo.mensagem + "\n", resumo.tag)

    def _loop_captura(self) -> None:
        while self.ligado:
            try:
                # REGRA INVARIANTE #1: reconciliar = olha Portal vs pasta destino do cliente
                # e re-importa o que faltar. NAO usa ledger interno como autoridade.
                # Cada chamada (a cada 60s) garante que arquivos apagados do destino voltem.
                mes = self._mes_operacao()
                self._log(f"\n--- Rodando reconciliar (Portal vs destino) - mes {mes} ---\n", "info")
                rc, saida = self._rodar(
                    comando_reconciliar(mes),
                    "reconciliar",
                )
                self._registrar_pos_reconciliacao(rc, saida, proximo_ciclo=True)
            except ValueError as e:
                self._log(f"Mes de atuacao invalido: {e}\n", "erro")
            except Exception as e:
                self._log(f"Loop reconciliar - erro: {e}\n", "erro")

            for _ in range(INTERVALO_SEGUNDOS):
                if not self.ligado:
                    return
                time.sleep(1)

    # ---------- DESLIGAR ----------
    def acao_desligar(self) -> None:
        if not self.ligado:
            self.btn_ligar.config(
                state=tk.NORMAL, text="LIGAR SISTEMA",
                bg="#16a085", activebackground="#1abc9c",
            )
            self.btn_verificar.config(state=tk.NORMAL)
            self.chk_automatico.config(state=tk.NORMAL)
            self._sincronizar_mes_automatico()
            return
        self.ligado = False
        self._titulo("DESLIGAR SISTEMA")

        for proc in self.processos:
            try:
                if proc.poll() is None:
                    self._log(f"Encerrando PID {proc.pid}...\n", "info")
                    proc.send_signal(signal.SIGTERM)
                    try:
                        proc.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        proc.kill()
            except Exception as e:
                self._log(f"Erro ao encerrar: {e}\n", "erro")
        self.processos.clear()

        self.lbl_status.config(text="● DESLIGADO", fg="#888")
        self.btn_ligar.config(
            state=tk.NORMAL, text="LIGAR SISTEMA",
            bg="#16a085", activebackground="#1abc9c",
        )
        self.btn_verificar.config(state=tk.NORMAL)
        self.chk_automatico.config(state=tk.NORMAL)
        self._sincronizar_mes_automatico()
        self._log("Sistema desligado.\n", "ok")


if __name__ == "__main__":
    Painel().root.mainloop()
