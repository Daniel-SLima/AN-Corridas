# Primo — Seu parceiro das contas

Aplicativo Android **offline e sem login** para registrar rapidamente corridas, gasolina, mecânico e metas de recebimento. Pensado para o Xiaomi Redmi 13C e outros aparelhos Android 8+.

> **Status**: código-fonte da versão 0.2.0. O APK só pode ser considerado validado após compilar e testar num Android real. Não requer Android Studio para compilar via GitHub Actions.

## Funcionalidades

- Bolinha móvel com botão `+` por cima de outros aplicativos (após autorização do usuário), com ações **Corrida**, **Gasolina** e **Mecânico**.
- Corrida: informa o valor, contabiliza uma corrida e permite origem Rua, 99 ou Uber Taxi e forma de pagamento.
- Gasolina: valor do abastecimento. Mecânico: valor **e descrição do serviço**.
- Resumos Hoje, Ontem, Semana (segunda a domingo), Mês e Outro dia.
- Saldo parcial = valores de corridas menos gastos registrados; **não é lucro líquido**.
- Histórico: ver, editar e excluir lançamentos.
- Metas **diária e semanal independentes e opcionais**, definidas em R$, com porcentagem e restante; o progresso usa **apenas receitas de corridas**.
- Mensagens curtas e bem-humoradas podem ser desligadas.
- Backup JSON e restauração. Metas também são exportadas; backups antigos `meutaxi-backup-v1` continuam aceitos.
- Marca Primo: identidade azul/amarelo, ícone e mascote na tela principal.

## Build sem Android Studio

1. Em GitHub, abra **Actions → Gerar APK Android**.
2. Aguarde o job `build` finalizar.
3. Abra os **Artifacts** do job e baixe `Primo-APK`.
4. Extraia o ZIP para obter o APK de depuração e instale no Redmi 13C, permitindo instalação de fontes externas somente para essa ação.

O projeto usa Android Gradle Plugin 9.1.1, Java 17, Gradle 9.3.1 e Android SDK 36, com Kotlin compilado pelo plugin Android. Você pode compilar localmente usando `gradle :app:assembleDebug` se tiver o Android SDK.

**Atenção à assinatura:** APK debug é assinado automaticamente com uma chave temporária que muda entre ambientes/builds de CI. Para atualizar a instalação sem perder dados, configure uma keystore permanente nos secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD` e utilize APK release assinado. Guarde a keystore em local seguro; não faça commit dela no repositório. Antes de trocar a assinatura, exporte backup e só então reinstale.

## Uso da bolinha no Xiaomi

Dentro do Primo: toque **Permitir bolinha flutuante**, aceite a sobreposição e depois toque **Ativar bolinha flutuante**. Em versões do MIUI/HyperOS, pode ser necessário ajustar economia de bateria/execução em segundo plano nas configurações do aparelho. Isso só é confirmado testando no aparelho; não há root e o Primo não controla o app de corridas, o mapa ou outras telas.

Se a bolinha for encerrada pelo Android, reabra o Primo e a ative novamente. Ela não deve reiniciar escondida, sem solicitação do usuário.

## Estrutura

- `app/src/main/java/br/com/meutaxi/app/Entries.kt`: banco local SQLite, registros, moeda e backup.
- `Goals.kt`: metas e preferências, cálculo de progressos.
- `MainActivity.kt`: resumo, histórico, metas e configurações.
- `FloatingService.kt`: sobreposição móvel e notificações.
- `Ui.kt`: componentes visuais e formulários de registro.
- `.github/workflows/android-apk.yml`: build automático e download do APK.

## Privacidade

Sem rastreadores, anúncios ou servidor. Os dados ficam no aparelho, sem internet exigida. O aplicativo não lê os dados privados de outros aplicativos. A restauração substitui registros atuais e solicita confirmação; faça exportação antes.

## Testes antes de entregar ao seu pai

1. Dar autorização de sobreposição no Redmi 13C (MIUI ou HyperOS).
2. Abrir o aplicativo 99, mover a bolinha e registrar uma corrida com o aparelho parado.
3. Registrar gasolina e mecânico com descrição, verificar soma e edição.
4. Configurar meta diária e semanal, registrar corrida, verificar progresso e restante.
5. Reiniciar o aplicativo e verificar se os dados persistiram.
6. Exportar backup, validar em instalação de teste sem apagar dados reais.
7. Testar como o sistema lida com tela bloqueada, pouca bateria e fim do serviço.
