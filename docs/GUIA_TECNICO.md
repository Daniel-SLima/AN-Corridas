# Primo — Guia técnico e manutenção

Referência da versão **1.0.0**. A apresentação do produto está no [README](../README.md); o texto anterior foi preservado integralmente em [HISTORICO_README.md](HISTORICO_README.md), incluindo evolução, assinatura e checklists de QA. Os trechos sobre versões antigas são registros históricos, não instruções da versão atual.

## Ambiente de compilação

| Configuração | Valor |
| --- | --- |
| Módulo | `app` |
| Linguagem | Kotlin, com suporte integrado ao Android Gradle Plugin |
| Android Gradle Plugin | 9.1.1 |
| Gradle usado no CI | 9.3.1 |
| Java | 17 |
| `compileSdk` / Build Tools do CI | 36 / 36.0.0 |
| `minSdk` / `targetSdk` | 26 / 35 |
| `applicationId` / namespace | `br.com.meutaxi.app` |
| `versionCode` / `versionName` | 14 / 1.0.0 |
| Banco local | `meutaxi.db`, schema versão 1 |

O repositório não inclui Gradle Wrapper. Instale/configure Gradle 9.3.1, Java 17 e Android SDK com a plataforma 36 para compilar localmente. Configure o SDK pelo ambiente ou por um `local.properties` local, sem versioná-lo.

```sh
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

O APK debug é gerado em `app/build/outputs/apk/debug/`. Ele tem assinatura diferente da release oficial; use um dispositivo ou ambiente separado para desenvolvimento, evitando desinstalar a release com registros reais.

## Mapa do código

Todos os arquivos Kotlin abaixo ficam em `app/src/main/java/br/com/meutaxi/app/`.

| Arquivo | Responsabilidade |
| --- | --- |
| `MainActivity.kt` | Navegação, períodos, resumo, histórico, metas, fechamento, backup e integração com os tutoriais. |
| `Entries.kt` | Modelo `Entry`, tipos de registro, moeda, `EntriesDb`, consultas e backup/restauração dos lançamentos. |
| `Goals.kt` | Preferências das metas, validação, início da semana e cálculos de progresso/restante. |
| `Ui.kt` | Componentes visuais e `QuickEntryForm`, usado na Activity e na sobreposição. |
| `FloatingService.kt` | Serviço em primeiro plano, notificação, bolinha móvel, menu e formulário sobre outros apps. |
| `PrimoWidgetProvider.kt` | Atalho do launcher para ativar a bolinha e abrir seu menu. |
| `ClosingReport.kt` | Totais por categoria, origem e pagamento, média, nome por período e CSV. |
| `ReportShareProvider.kt` | Acesso somente de leitura aos CSVs em cache, via permissões temporárias de URI. |
| `PrimoTour.kt` | Guias do aplicativo e da bolinha simulada, com spotlight e navegação entre etapas. |
| `PrimoIcons.kt` | Ícones desenhados com as APIs gráficas do Android. |

As telas usam Views do framework Android. Não há Compose, Room, backend ou sincronização remota no projeto. A única dependência declarada em `app/build.gradle.kts` é JUnit 4.13.2 para testes.

## Regras de dados e relatórios

- Valores monetários são armazenados como `Long` em centavos. Os lançamentos exigem valor positivo e uma categoria válida.
- Receita corresponde a corridas; gasolina e mecânico são despesas. Saldo parcial = receitas menos despesas registradas.
- Metas são independentes e opcionais. O progresso usa somente receitas de corridas; a semana começa na segunda-feira.
- Consultas usam início inclusivo e fim exclusivo, com datas no fuso do aparelho. O app atualiza a tela ao retornar e na virada do dia.
- A edição mantém o identificador e o horário do lançamento. O desfazer consulta a inserção mais recente, pelo ID.
- O CSV usa ponto e vírgula, linhas CRLF e codificação Windows-1252. Os textos são normalizados para ASCII legível, sem alterar os textos originais do banco e do backup. Conteúdo textual é escapado para impedir interpretação como fórmula.
- O arquivo de fechamento segue `primo-fechamento-AAAA-MM-DD_a_AAAA-MM-DD.csv`, com resumo e seções de corridas, gasolina e mecânico, ganhos e gastos em colunas separadas.

## Backup e privacidade

O aplicativo usa o formato `meutaxi-backup-v1`. A Activity acrescenta as metas ao JSON exportado; backups antigos sem metas continuam aceitos. A restauração valida os lançamentos antes de substituí-los e faz a alteração do SQLite em transação. Quando há metas no arquivo, elas são validadas antes da restauração e salvas em SharedPreferences após os registros.

A importação substitui os lançamentos atuais. Exporte uma cópia antes. O indicador de último backup só muda após exportação concluída e é limpo ao importar outra base.

Não há permissão de internet no Manifest. Os dados ficam localmente; arquivos de backup e relatório saem do app somente pela exportação/compartilhamento solicitado pelo usuário. O backup automático do Android está desativado (`allowBackup=false`).

## APK oficial e assinatura permanente

O workflow [Gerar APK Android assinado](../.github/workflows/android-apk.yml) roda em push para `main` ou acionamento manual. Ele configura Java/SDK/Gradle, exige os secrets de assinatura, descriptografa a chave temporariamente, verifica o certificado, executa os testes, compila a release e confere o certificado do APK. A cópia temporária da chave é removida ao final.

Secrets exigidos:

- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

A cópia versionada da chave é criptografada com AES-256-CBC e PBKDF2, com 400.000 iterações. A senha permanece no secret. O secret antigo `ANDROID_KEYSTORE_BASE64` não é usado. Nunca publique a keystore original, senhas ou `primo-gh-secrets.env`. Preserve o backup privado da assinatura fora do repositório.

A impressão digital pública esperada está em [PRIMO_SIGNING_SHA256](../.github/PRIMO_SIGNING_SHA256). Para atualizar por cima preservando dados, mantenha o mesmo `applicationId`, a mesma chave e um `versionCode` crescente.

O artefato do workflow é **Primo-APK-Release**, contendo `app-release.apk`, com retenção de **14 dias**. Para distribuição ao usuário, a [Release v1.0.0](https://github.com/Daniel-SLima/Primo-Android/releases/tag/v1.0.0) disponibiliza o mesmo APK assinado como [Primo_v1.0.0_release.apk](https://github.com/Daniel-SLima/Primo-Android/releases/download/v1.0.0/Primo_v1.0.0_release.apk), sem depender da expiração do artefato do CI.

O arquivo publicado foi gerado na [execução 38311802540](https://github.com/Daniel-SLima/Primo-Android/actions/runs/38311802540), do commit `2779bf2412e920c93e955e020af2b07e83b24287`. Antes da publicação, `apksigner` confirmou o certificado permanente e `aapt` confirmou `versionName=1.0.0`, `versionCode=14`, `applicationId=br.com.meutaxi.app` e `minSdk=26`.

SHA-256 do APK: `ce53f436457069bb3ed9dc4f49977b36b94d036a77817b1a98c1f674caad66dd`.

### Migração histórica de APK debug

A versão 0.3.0 debug não compartilha a assinatura permanente. O procedimento registrado é exportar um backup antes de desinstalar, instalar a release e importar a cópia. Atualizações entre releases oficiais devem ser feitas por cima, sem desinstalar.

## Sobreposição e widget

A bolinha precisa de `SYSTEM_ALERT_WINDOW` e usa um serviço em primeiro plano do tipo `specialUse`, com notificação. O serviço retorna `START_NOT_STICKY` para evitar reinício inesperado após encerramento do processo. Não há inicialização automática no boot.

O widget solicita abertura do menu; sem permissão de sobreposição, encaminha para o app. A instalação do widget depende do launcher. Android, MIUI e HyperOS podem limitar execução em segundo plano: validar no aparelho, orientar sobre bateria e reativar manualmente quando necessário.

## Validação

Há nove testes unitários em `ReportExportTest.kt` e `PrimoTourStepsTest.kt`: seis para totais/CSV, incluindo textos, fórmulas e mil lançamentos, e três para a sequência e o conteúdo dos guias. Eles não validam a interface Android ou a sobreposição em dispositivo real.

Para QA manual, consulte:

- [Entrega e verificação da 1.0.0](ENTREGA_PRIMO_1.0.0.md).
- [Tutoriais e simulação da bolinha](TUTORIAL_PRIMO_0.9.0.md).
- [Spotlight e acabamento visual da 0.9.1](ACABAMENTO_PRIMO_0.9.1.md).
- [Checklists históricos do README anterior](HISTORICO_README.md).

Teste atualização sem perda de dados, permissão e arraste nos cantos, salvamento, correções, metas, fechamento, CSV, backup e mudança de data. O README histórico menciona `docs/PILOTO_PRIMO_0.8.0.md`, mas esse arquivo não existe no checkout atual; as orientações disponíveis do piloto estão no próprio histórico.
