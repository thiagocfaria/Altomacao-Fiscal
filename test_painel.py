from datetime import datetime

import painel


def test_mes_atuacao_automatico_usa_mes_vigente():
    assert painel.mes_atuacao(True, "2026-04", datetime(2026, 5, 12, 8, 30)) == "2026-05"


def test_mes_atuacao_manual_usa_mes_informado():
    assert painel.mes_atuacao(False, "2026-04", datetime(2026, 5, 12, 8, 30)) == "2026-04"


def test_mes_atuacao_rejeita_manual_invalido():
    try:
        painel.mes_atuacao(False, "2026/04", datetime(2026, 5, 12, 8, 30))
    except ValueError as exc:
        assert "AAAA-MM" in str(exc)
    else:
        raise AssertionError("mes manual invalido deveria falhar")


def test_comando_verificar_tudo_usa_mes_escolhido():
    comando = painel.comando_verificar_tudo("2026-04")

    assert "verificar-tudo" in comando
    assert comando[comando.index("--mes") + 1] == "2026-04"
    assert comando[comando.index("--ambiente") + 1] == painel.AMBIENTE
    assert comando[comando.index("--nsu") + 1] == painel.NSU_INICIAL
    assert comando[comando.index("--max-lotes") + 1] == painel.MAX_LOTES_RECONCILIACAO
    assert comando[comando.index("--tentativas-portal") + 1] == painel.PORTAL_TENTATIVAS
    assert comando[comando.index("--retry-portal-ms") + 1] == painel.PORTAL_RETRY_BASE_MS


def test_comando_reconciliar_usa_mes_ambiente_nsu_e_max_lotes():
    comando = painel.comando_reconciliar("2026-04")

    assert "reconciliar" in comando
    assert comando[comando.index("--mes") + 1] == "2026-04"
    assert comando[comando.index("--ambiente") + 1] == painel.AMBIENTE
    assert comando[comando.index("--nsu") + 1] == painel.NSU_INICIAL
    assert comando[comando.index("--max-lotes") + 1] == painel.MAX_LOTES_RECONCILIACAO
    assert comando[comando.index("--tentativas-portal") + 1] == painel.PORTAL_TENTATIVAS
    assert comando[comando.index("--retry-portal-ms") + 1] == painel.PORTAL_RETRY_BASE_MS


def test_comando_renomeador_preflight_usa_mes_escolhido():
    comando = painel.comando_renomeador_preflight("2026-04")

    assert comando[:4] == ["java", "-jar", painel.JAR_RENOMEADOR, "config"]
    assert "preflight" in comando
    assert comando[comando.index("--mes") + 1] == "2026-04"


def test_jars_ausentes_bloqueiam_verificacao(tmp_path):
    import_jar = tmp_path / "importador.jar"
    renomeador_jar = tmp_path / "renomeador.jar"
    import_jar.write_text("jar")

    assert painel.jars_ausentes(import_jar, renomeador_jar) == [renomeador_jar]


def test_resultado_final_so_e_tudo_ok_quando_todos_comandos_retornam_zero():
    assert painel.classificar_resultado_verificacao([0, 0, 0]) == "OK"
    assert painel.classificar_resultado_verificacao([1, 0, 0]) == "ATENCAO"
    assert painel.classificar_resultado_verificacao([0, 1, 0]) == "BLOQUEADO"
    assert painel.classificar_resultado_verificacao([0, 0, 1]) == "BLOQUEADO"
    assert painel.classificar_resultado_verificacao([2, 0, 0]) == "BLOQUEADO"
    assert painel.classificar_resultado_verificacao([4, 0, 0]) == "ERRO_EXTERNO"


def test_ok_para_ligar_invalida_quando_parametros_mudam():
    verificacao = painel.ParametrosVerificacao("2026-04", "PRODUCAO", "1", "500", "5", "2000")

    assert painel.verificacao_continua_valida(verificacao, "2026-04", "PRODUCAO", "1", "500", "5", "2000")
    assert not painel.verificacao_continua_valida(verificacao, "2026-05", "PRODUCAO", "1", "500", "5", "2000")
    assert not painel.verificacao_continua_valida(verificacao, "2026-04", "PRODUCAO", "1", "500", "3", "2000")


def test_tag_linha_log_destaca_bloqueado_como_erro():
    assert painel.tag_linha_log("NIVEL: BLOQUEADO") == "erro"
    assert painel.tag_linha_log("PODE LIGAR? NAO") == "erro"
    assert painel.tag_linha_log("NIVEL: ATENCAO") == "erro"
    assert painel.tag_linha_log("NIVEL: OK") == "ok"


def test_documentos_reimportados_prefere_total_final_da_saida():
    saida = """
- DGA ENERGIA [25014360000173]
  Re-importados: 2
- POWER SOURCE [26474286000211]
  Re-importados: 1
Documentos re-importados: 3
"""

    assert painel.documentos_reimportados_saida(saida) == 3


def test_resumo_pos_reconciliacao_primeira_rodada_vazia_confirma_na_proxima():
    health = painel.HealthRenomeador(
        status="OK",
        total=0,
        revisar=0,
        ignorados=0,
        erros=0,
        mensagem="varredura periodica concluida",
    )

    resumo = painel.resumo_pos_reconciliacao(
        0,
        "Documentos re-importados: 0",
        health,
        intervalo_segundos=60,
        rodadas_sem_pendencia=1,
        proximo_ciclo=True,
    )

    assert resumo.sem_pendencia_ativa
    assert not resumo.tudo_conferido
    assert "confirmando na proxima rodada" in resumo.mensagem
    assert "Proxima conferencia em 60s" in resumo.mensagem
    assert "5h" not in resumo.mensagem


def test_resumo_pos_reconciliacao_segunda_rodada_vazia_declara_tudo_conferido():
    health = painel.HealthRenomeador(
        status="ATENCAO",
        total=0,
        revisar=0,
        ignorados=1,
        erros=0,
        mensagem="evento processado",
    )

    resumo = painel.resumo_pos_reconciliacao(
        0,
        "Documentos re-importados: 0",
        health,
        intervalo_segundos=60,
        rodadas_sem_pendencia=2,
        proximo_ciclo=True,
    )

    assert resumo.sem_pendencia_ativa
    assert resumo.tudo_conferido
    assert "Tudo conferido nesta rodada" in resumo.mensagem
    assert "ignorado(s) recentes" in resumo.mensagem
    assert "Proxima conferencia em 60s" in resumo.mensagem


def test_resumo_pos_reconciliacao_nao_declara_tudo_com_pendencia_ativa():
    health = painel.HealthRenomeador(
        status="ATENCAO",
        total=1,
        revisar=1,
        ignorados=0,
        erros=0,
        mensagem="evento processado",
    )

    resumo = painel.resumo_pos_reconciliacao(
        0,
        "Documentos re-importados: 0",
        health,
        intervalo_segundos=60,
        rodadas_sem_pendencia=0,
        proximo_ciclo=True,
    )

    assert not resumo.sem_pendencia_ativa
    assert not resumo.tudo_conferido
    assert "RENOMEADOR ainda tem pendencia" in resumo.mensagem
