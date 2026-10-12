# Relatório — Apresentação do Primo no GitHub

Data: **12/10/2026**. Repositório confirmado: `Daniel-SLima/Primo-Android`, branch `main`. O checkout estava sem alterações locais antes do trabalho.

## Entrega documental

- [README](../README.md) reescrito como apresentação do produto: mascote original, identidade azul/amarela, benefícios, galerias, destaque da bolinha, instalação, primeiros passos e tecnologias.
- [Guia técnico](GUIA_TECNICO.md) criado com ambiente de build, responsabilidades dos arquivos, regras financeiras, CSV, backup, assinatura e QA.
- [README anterior](HISTORICO_README.md) preservado integralmente, incluindo histórico de versões e instruções técnicas. A comparação com `HEAD:README.md` confirmou o mesmo conteúdo, considerando a normalização de finais de linha do Git.
- Oito capturas reais organizadas em `docs/images/`, com nomes descritivos e texto alternativo no README.

## Capturas e tratamento

| Conteúdo confirmado visualmente | Arquivo | Tratamento |
| --- | --- | --- |
| Início e resumo financeiro | [primo-inicio.webp](images/primo-inicio.webp) | Conversão para WebP, mantendo a captura completa. |
| Metas diária e semanal | [primo-metas.webp](images/primo-metas.webp) | Conversão para WebP, mantendo a captura completa. |
| Mais: fechamento e configurações | [primo-fechamento.webp](images/primo-fechamento.webp) | Conversão para WebP; a legenda identifica a aba Mais, sem apresentá-la como tela de relatório detalhado. |
| Menu da bolinha sobre o Maps | [primo-bolinha.webp](images/primo-bolinha.webp) | Recorte do menu e do `+`; contexto do mapa desfocado para ocultar nomes/localização. Avatar e restante do mapa excluídos. |
| Formulário de corrida | [primo-nova-corrida.webp](images/primo-nova-corrida.webp) | Recorte do formulário, excluindo busca, avatar e localização. |
| Histórico com editar e excluir | [primo-historico.webp](images/primo-historico.webp) | Conversão para WebP, mantendo a captura completa. |
| Formulário de mecânico | [primo-mecanico.webp](images/primo-mecanico.webp) | Recorte do formulário e margem neutra para alinhar a galeria. |
| Formulário de gasolina | [primo-gasolina.webp](images/primo-gasolina.webp) | Recorte do formulário e margem neutra para alinhar a galeria. |

Os três formulários usam quadros de 630 × 752 px; o conteúdo original não foi ampliado, esticado nem redesenhado. As imagens originais fornecidas e o arquivo original do mascote não foram modificados. Não foram criadas telas, resultados financeiros ou recursos fictícios.

Tamanho combinado: **653.912 bytes → 265.004 bytes**, redução de aproximadamente **59,5%**. Otimização com WebP de qualidade 88; sem aumento de resolução do conteúdo.

## Conferência de conteúdo e instalação

As funcionalidades foram confrontadas com `MainActivity`, `Entries`, `Goals`, `QuickEntryForm`, `FloatingService`, `PrimoWidgetProvider`, `ClosingReport`, `ReportShareProvider` e `PrimoTour`, além do Manifest e dos arquivos Gradle.

O README diferencia seleção manual de origem de integração com plataformas, saldo parcial de lucro líquido e metas de receita de metas de lucro. Também descreve as restrições do Android para a bolinha e a substituição dos registros na importação.

A consulta à API do GitHub confirmou ausência de releases e de licença definida. Havia artefatos `Primo-APK-Release` disponíveis. A seção de instalação aponta para o workflow existente e informa retenção de 14 dias; não foi criado link direto para um APK inexistente.

## Verificação realizada

- Renderização pela API oficial de Markdown do GitHub, no modo GFM, confirmando manutenção das galerias e dos atributos de imagem pelo sanitizador.
- Oito capturas, mascote e quatro badges presentes na renderização: **13 imagens**.
- Caminhos locais, imagens WebP e atalhos internos do README verificados.
- Prévia local do HTML retornado pelo GitHub revisada em **1200 × 900** e **390 × 844**, sem imagens quebradas ou rolagem horizontal da página. As galerias se reorganizam em linhas conforme a largura disponível.
- Ortografia e consistência revisadas; `git diff --check` sem erros.
- Código Android, recursos do app, Gradle, workflows, assinatura e documentação preexistente sem alterações.

A prévia usa HTML retornado pelo GitHub e estilos locais que aproximam sua apresentação. Na entrega inicial, os arquivos permaneceram locais, sem commit, push, publicação de release ou deploy. Depois dessa revisão, o proprietário autorizou o commit e o envio da documentação para a branch `main`. Essa autorização não inclui publicar releases ou uma landing page. Os testes Android não foram executados porque a alteração é exclusivamente documental.

## Observação sobre o histórico

O README anterior contém uma referência textual a `docs/PILOTO_PRIMO_0.8.0.md`, arquivo ausente no checkout inicial. Essa referência foi mantida no arquivo histórico para preservar o conteúdo; o novo README aponta somente para documentos existentes.
