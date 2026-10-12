# Primo — Seu parceiro das contas

Aplicativo Android **offline e sem login** para registrar rapidamente corridas, gasolina, mecânico e metas de recebimento. Pensado para o Xiaomi Redmi 13C e outros aparelhos Android 8+.

> **Status**: código-fonte da versão 0.9.0. O APK só pode ser considerado validado após compilar e testar num Android real. Não requer Android Studio para compilar via GitHub Actions.

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

**Assinatura permanente:** GitHub Actions gera somente APK release assinado; conserve a mesma chave, o applicationId e incremente o versionCode. O secret antigo ANDROID_KEYSTORE_BASE64 não é mais utilizado.

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

## Primo 0.4.0 — melhorias de usabilidade

- Navegação inferior fixa entre **Início**, **Histórico**, **Metas** e **Mais**; o registro rápido continua disponível na tela inicial.
- Ícones vetoriais uniformes para corrida, combustível, manutenção, menus e lançamentos, sem fonte externa nem Internet.
- Animação curta ao mudar de área, ao expandir/recolher o menu flutuante, ao arrastar a bolinha e na barra das metas.
- O menu da bolinha passa a usar a **altura medida** para se manter visível e segue o arraste, escolhendo automaticamente a área disponível à esquerda, à direita, acima ou abaixo.
- A bolinha se acomoda perto da borda após arrastar. O gesto de arrastar **não desativa nem oculta** o serviço.
- Metas ativas aparecem em formato compacto **abaixo de Mecânico** na tela principal e no menu da bolinha; prioridade para meta diária, com semanal como alternativa. Exibe apenas o valor que falta, ou **Meta concluída** após atingir o objetivo. Valores superiores à meta ficam nas telas de relatórios/metas.
- Mantidas a assinatura permanente do release, o mesmo identificador do aplicativo, os dados locais SQLite e o formato dos backups, com `versionCode` incremental para atualizar por cima da instalação 0.3.1.

### Checklist manual de QA para 0.4.0

1. Instalar **sem desinstalar** a 0.3.1 e confirmar que o histórico e as metas são preservados.
2. Arrastar a bolinha para quatro cantos com o menu aberto: as três ações e a meta devem ficar acessíveis e acompanhar o movimento.
3. Conferir o funcionamento da bolinha sobre a 99, sem sobrepor controles críticos, sempre com o carro parado.
4. Salvar corrida, gasolina e mecânico; conferir confirmação verde e fechamento do painel.
5. Comparar a meta compacta antes, no momento e após atingir o valor diário; testar também somente a semanal e nenhuma meta.
6. Verificar a navegação fixa, consulta a outros dias, edição e exclusão no histórico, backup e restauração.
7. Fechar, reabrir e reiniciar o aparelho; conferir dados preservados e que a bolinha não ativa sozinha se estiver desativada.

A compilação automática é um teste técnico do código. A compatibilidade específica com o Redmi 13C/MIUI/HyperOS precisa de validação no próprio aparelho.

## Primo 0.5.0 — fechamento, widget e refinamentos

- **Mais → Fechamento financeiro**: receitas por origem (Rua/99/Uber Taxi), por meio de pagamento, despesas separadas por gasolina/mecânico, total, média de corrida e saldo parcial.
- **Exportar fechamento CSV** do período selecionado (Hoje, Ontem, Semana, Mês ou outro dia), com delimitador ponto e vírgula, UTF-8 BOM e tratamento de textos contra fórmulas maliciosas em planilhas.
- **X ao lado da meta no menu flutuante**: fecha a bolinha imediatamente, mesmo sem meta configurada; tocar no + continua expandindo/recolhendo. Desligar não apaga nenhum lançamento.
- **Widget Primo na tela inicial do Android**: toque para ativar e abrir o menu da bolinha, sem navegar pelas telas do app. Sem permissão de sobreposição, abre o Primo para orientar o usuário. Alguns sistemas MIUI/HyperOS podem exigir liberação de execução em segundo plano.
- **Cabeçalho com mais respiro**: tratamento dos recortes/status bar no Android 15/16 e margem de 12dp no topo.
- **Mecânico redesenhado**: chave inglesa vetorial mais clara, legível em tamanhos pequenos.
- Mantidos o banco SQLite, o backup JSON, o identificador do app e assinatura fixa. `versionCode=6`; instalar sobre a 0.4.0.

### QA sugerido 0.5.0

1. Atualizar a 0.4.0 diretamente sem perder as corridas nem metas.
2. Conferir a margem do topo em Samsung Android 16 e Redmi 13C, com barra de status visível.
3. Conferir ícone Mecânico na home, histórico e menu flutuante (sem cortes).
4. Abrir a bolinha, tocar no X e verificar remoção imediata, sem apagar dados; reativar pelo widget.
5. Adicionar o widget Primo pelo menu de widgets do launcher; tocar quando serviço estiver ligado, desligado e com sobreposição desautorizada.
6. Fechamento: comparar Rua/99/Uber, formas de pagamento e gastos com o histórico.
7. Exportar CSV e conferir acentos, valores negativos, separador e texto livre seguro na planilha.
8. Testar confirmação verde, meta concluída e bolinha arrastável na nova versão.

## Primo 0.5.1 — estabilidade, compartilhamento e Excel

- Em **Mais**, o cartão **Primo na tela inicial** tenta sugerir a instalação do widget pelo launcher Android; oferece instruções se o aparelho não der suporte.
- No Fechamento financeiro, **Salvar Excel / Compartilhar Excel** gera **.xlsx real**, com abas **Resumo** e **Lançamentos**, células de valores numéricos e acentuação Unicode sem erro de CSV.
- Opções separadas: **CSV compatível (Windows-1252)** para o visualizador que mostrou DescriÃ§Ã£o e **CSV UTF-8 sem BOM** para importadores com seleção de charset. O formato recomendado é Excel.
- Compartilhamento usa ContentProvider específico somente de leitura, com URI temporária para o seletor do Android; sem acesso aos registros internos.
- Layout do fechamento apresenta origem, pagamento e gastos com descrições em duas linhas, evitando colisão com valores. Os totais são recalculados diretamente do SQLite a cada abertura.
- Registro duplicado: o salvamento já impede duplo toque durante a confirmação. Edições agora conferem que realmente alteraram uma linha do banco antes de confirmar.
- A tela refresca ao voltar e na virada da meia-noite (sem serviço extra), mantendo metas e histórico do novo dia consistentes. A restauração do backup segue validada e transacional quanto aos registros.
- Mesmo applicationId, assinatura permanente e banco de dados. `versionCode=7`. Atualização direta por cima da versão 0.5.0.

### Testes
1. Regressão do histórico após atualização e importação de backup; nunca desinstalar previamente.
2. Comparar fechamento por origem e pagamento com valores reais de corridas, incluindo edição e exclusão.
3. Confirmar os acentos em Excel (.xlsx), CSV compatível (Windows-1252) no visualizador Android e CSV UTF-8 em importador configurado para UTF-8.
4. Compartilhar XLSX e CSV com WhatsApp ou outro app e conferir leitura sem expor o arquivo privado do banco.
5. Testar sugestão do widget em Samsung e Xiaomi; fallback manual quando indisponível.
6. Testar mudança de data com app aberto, repetição do botão Salvar e backup sem perda de dados.

## Primo 0.6.0 — Fechamento simplificado
- Em Mais, apenas três botões agrupados: Ver, Baixar e Compartilhar. A tela detalhada mantém somente Baixar e Compartilhar.
- Formato único de saída: CSV compatível Windows-1252. Exportação Excel foi retirada do aplicativo. Nome do arquivo identifica o período.
- Ícones vetoriais e áreas de toque grandes preservam a legibilidade.
- Banco de dados, backup e certificado permanente mantidos; versão interna 8, maior do que os builds anteriores.

## Primo 0.7.0 — CSV com período e modo simples

- Nome de arquivo em downloads e compartilhamento: `primo-fechamento-AAAA-MM-DD_a_AAAA-MM-DD.csv`. O conteúdo identifica o início e o fim do período.
- CSV único Windows-1252, com cabeçalho **Data, Hora, Categoria, Origem, Pagamento, Descrição, Ganhos (R$), Gastos (R$)**.
- Seções consecutivas: **Corridas**, **Gasolina** e **Mecânico**. Resumo inicial com corridas, combustíveis, manutenções e saldo parcial. Despesas usam sua própria coluna, sem confundir receitas e gastos.
- **Modo simples** (ativo inicialmente): botões grandes de Corrida, Gasolina e Mecânico na tela inicial, com menos texto. Pode ser desativado em Mais.
- **Resumo do dia** na tela inicial: apresenta ganhos, despesas, número de corridas e saldo parcial, sem finalizar nem apagar nada.
- **Desfazer último lançamento** em Mais: mostra o registro de inserção mais recente, exige confirmação para excluir ou permite editar.
- Compartilhamento usa URI temporária limitada a arquivos CSV com nome de período, sem expor o banco.
- Banco SQLite e assinatura permanente não foram alterados; versão interna aumentada para 9.

### QA 0.7.0
1. Atualizar por cima da 0.6.0 sem perder as 877 entradas da base fictícia ou registros reais.
2. Exportar Hoje, Semana, Mês e Dia; conferir intervalo no nome **e** no CSV, tanto no download quanto no WhatsApp.
3. Confirmar linhas diárias e seções Corridas, Gasolina e Mecânico, com ganhos e gastos em colunas distintas.
4. Conferir Saldo parcial = recebido menos gastos, incluindo dias só com despesas.
5. Ativar/desativar Modo simples; reiniciar e conferir preferência salva.
6. Resumo do dia: verificar corridas/recebido/gastos; não deve apagar informações.
7. Desfazer último: Cancelar, Editar e Excluir; exigir confirmação; verificar recálculo.
8. Stress-test: histórico extenso, mudança de dia, salvar na bolinha e backup/restauração.

## Primo 0.7.1 — ajustes após o teste no Samsung

- Removido o **Modo simples** da tela inicial e das configurações. Restaurados os botões, espaçamentos e saudação da interface aprovada anteriormente. **Resumo do dia** e **Desfazer último lançamento** permanecem.
- A meta dentro da bolinha foi redesenhada em duas linhas com altura 64 dp e menu com largura 266 dp; evita o corte do valor ao lado do X e preserva o reposicionamento nas bordas.
- A compatibilidade do CSV agora prioriza leitura sem caracteres quebrados em qualquer interpretador (Google Sheets, Excel e WhatsApp): textos no arquivo são normalizados em ASCII legível, sem mudar os acentos que continuam salvos no app e no backup. Por exemplo, `Descrição` → `Descricao`, `Mecânico` → `Mecanico`, `óleo` → `oleo`. As datas, valores, categorias e nome com período não mudam.
- Regressão automática do CSV, cálculos, escapamento de fórmulas, 1.000 registros e consistência dos resultados.
- Mesma assinatura fixa, applicationId e dados locais. `versionCode=10`; atualização direta por cima da 0.7.0.

## Primo 0.8.0 — piloto de trabalho diário

- A versão **0.7.1 foi aprovada em 5/5 testes manuais**. A 0.8.0 mantém o mesmo visual e funcionalidades validadas.
- O **Resumo do dia** oferece **Corrigir última** quando há um lançamento registrado hoje. A opção abre a edição existente sem apagar nem duplicar dados.
- Em **Mais → Pronto para trabalhar**, é possível verificar a permissão para sobreposição, conferir a última data de exportação de backup registrada pelo Primo neste aparelho e reativar manualmente a bolinha.
- **Dicas para manter ativa** explicam como chegar às configurações do Primo no Android e orientam usuários Xiaomi/Redmi. Não há tentativa de contornar restrições do sistema nem de iniciar o serviço sozinho ao ligar o aparelho.
- O aviso **Último backup** só atualiza após a exportação finalizar sem erro; depois de importar outra base de registros, a indicação anterior é limpa para não dar falsa segurança.
- Serviço de bolinha agora só cria a sobreposição quando o serviço em primeiro plano foi realmente iniciado com sucesso.
- Sem novo login, rede ou banco. Mantidos assinatura, applicationId, SQLite, backup JSON e CSV; `versionCode=11`.
- Consulte `docs/PILOTO_PRIMO_0.8.0.md` para os testes de expediente e recuperação segura.

## Primo 0.9.0 — Tutoriais guiados com o mascote

- **Primeira abertura após atualizar**: apresenta um convite opcional para conhecer o Primo; não bloqueia o aplicativo e não reaparece automaticamente depois de responder.
- **Tutorial do app**: 11 etapas (boas-vindas, Corrida, Gasolina, Mecânico, Metas, Editar meta, Histórico, Mais, Fechamento, Backup e conclusão). Escurece a tela, destaca controles reais e avança por toque no destaque ou por Próximo.
- **Tutorial da bolinha**: 6 etapas com bolinha **simulada**, incluindo +, menu, meta, X e movimento. Não pede permissão de sobreposição e nunca salva lançamentos.
- O mascote original aparece como avatar circular, com mini expressões, balões de conversa e botões **Voltar, Pular, Próximo e Concluir**.
- **Mais → Aprenda com o Primo** permite rever cada tutorial separadamente.
- Durante o tutorial, tocar na área iluminada **não executa** os botões reais: navega apenas entre etapas, evitando importação de backup, alterações de metas e novos registros.
- Mesmo applicationId, SQLite, CSV, widget e assinatura permanente; `versionCode=12` permite atualizar a 0.8.0 diretamente.
