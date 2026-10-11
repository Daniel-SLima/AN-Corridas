# Primo — Seu parceiro das contas

Aplicativo Android **offline e sem login** para registrar rapidamente corridas, gasolina, mecânico e metas de recebimento. Pensado para o Xiaomi Redmi 13C e outros aparelhos Android 8+.

> **Status**: código-fonte da versão 0.3.1. O APK só pode ser considerado validado após compilar e testar num Android real. Não requer Android Studio para compilar via GitHub Actions.

## Funcionalidades

- Bolinha móvel com botão `+` por cima de outros aplicativos (após autorização do usuário), com ações **Corrida**, **Gasolina** e **Mecânico**.
- Corrida: informa o valor, contabiliza uma corrida e permite origem Rua, 99 ou Uber Taxi e forma de pagamento.
- Gasolina: valor do abastecimento. Mecânico: valor **e descrição do serviço**.
- Resumos Hoje, Ontem, Semana (segunda a domingo), Mês e Outro dia.
- Saldo parcial = valores de corridas menos gastos registrados; **não é lucro líquido**.
- Histórico: ver, editar e excluir lançamentos.
- Metas **diária e semanal independentes e opcionais**, definidas em R$, com porcentagem e restante; o progresso usa **apenas receitas de corridas**.
- Mensagens bem-humoradas vêm **desligadas por padrão** (também ao atualizar da v0.2); podem ser ativadas manualmente nas configurações. Não há narração de áudio.
- Registro via bolinha: o botão Salvar fica **verde com ✓ somente após gravar com sucesso**, as janelas fecham e a bolinha fica verde por aproximadamente 1 segundo, voltando depois ao `+` amarelo.
- Enquanto a bolinha é movida, as ações abertas seguem sua posição e se adaptam ao espaço disponível; limitações dos recortes da tela devem ser testadas no dispositivo.
- Backup JSON e restauração. Metas também são exportadas; backups antigos `meutaxi-backup-v1` continuam aceitos.
- Marca Primo: identidade azul/amarelo, ícone e mascote na tela principal.

## Build sem Android Studio

1. Em GitHub, abra **Actions → Gerar APK Android assinado**.
2. Aguarde o job `build` finalizar.
3. Abra os **Artifacts** do job e baixe `Primo-APK-Release`.
4. Extraia o ZIP para obter o APK release assinado e instale no Redmi 13C, permitindo instalação de fontes externas somente para essa ação.

O projeto usa Android Gradle Plugin 9.1.1, Java 17, Gradle 9.3.1 e Android SDK 36, com Kotlin compilado pelo plugin Android. Você pode compilar localmente usando `gradle :app:assembleDebug` se tiver o Android SDK.

**Atenção à assinatura:** APK debug é assinado automaticamente com uma chave temporária que muda entre ambientes/builds de CI. Para atualizar a instalação sem perder dados, configure uma keystore permanente nos secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD` e utilize APK release assinado. Guarde a keystore em local seguro; não faça commit dela no repositório. Antes de trocar a assinatura, exporte backup e só então reinstale.

### Assinatura permanente (Primo 0.3.1 em diante)

Desde 0.3.1, o GitHub Actions **só produz APK release assinado**: se faltarem os três secrets necessários, ele falha em vez de criar um APK debug. O certificado é verificado antes e depois da compilação.

**Assinatura no GitHub:** o arquivo `.github/primo-release.jks.enc` contém somente uma cópia **criptografada** da chave, usando AES-256-CBC e PBKDF2 (400.000 iterações). A senha de criptografia é longa e aleatória, mantida exclusivamente no secret `ANDROID_KEYSTORE_PASSWORD`. A keystore ORIGINAL nunca deve ser enviada ao repositório. O antigo secret `ANDROID_KEYSTORE_BASE64` não é mais utilizado e pode ser removido do GitHub por segurança.

1. Preserve o ZIP privado de assinatura entregue separadamente ao proprietário, em backup offline; ele é essencial para recuperar a chave e fazer futuras atualizações.
2. Configure `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD` em Settings → Secrets and variables → Actions → Repository secrets. O secret Base64 não é mais necessário.
3. Abra Actions → Gerar APK Android assinado → Run workflow. Baixe o artefato `Primo-APK-Release`.
4. **Migração da v0.3.0 debug:** antes de desinstalar, use Exportar backup. Instale a primeira release com assinatura permanente e use Importar backup. A chave privada da v0.3.0 debug não está disponível, portanto é necessária uma última desinstalação.
5. Futuras versões devem manter o `applicationId`, a mesma keystore e `versionCode` crescente para atualizar por cima preservando registros.

Impressão digital pública SHA-256: `12:53:89:DE:DA:49:21:5B:78:19:4D:B9:31:2D:D1:E1:1E:86:DE:F7:97:E0:0E:07:80:59:02:96:AD:F4:BF:D3`.

**NUNCA** publique a keystore `.jks`, `primo-gh-secrets.env` ou senhas em commits, issues ou prints. O arquivo `.jks.enc` versionado é criptografado, mas deve continuar protegido por uma senha forte e secreta.

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
2. Abrir a 99, expandir o menu e arrastar a bolinha: os botões devem segui-la sem sair da tela. Registrar corrida com o aparelho parado e conferir o ✓ verde e fechamento do menu.
3. Registrar gasolina e mecânico com descrição, verificar soma e edição.
4. Configurar meta diária e semanal, registrar corrida, verificar progresso e restante.
5. Reiniciar o aplicativo e verificar se os dados persistiram.
6. Exportar backup, validar em instalação de teste sem apagar dados reais.
7. Testar como o sistema lida com tela bloqueada, pouca bateria e fim do serviço.
