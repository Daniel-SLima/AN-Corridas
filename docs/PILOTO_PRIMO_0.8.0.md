# Primo 0.8.0 — Piloto de uso real (7 dias)

**Objetivo:** verificar se um motorista consegue registrar o trabalho diário com poucos toques e recuperar-se de interrupções do Android. Não é necessário usar um carro real para os testes técnicos.

## Antes de começar

- Instale o Primo 0.8.0 **por cima** da versão atual, sem desinstalar.
- Entre em **Mais → Exportar backup** e guarde o arquivo JSON fora do aplicativo (Downloads, armazenamento pessoal). Confirme que o sistema informou **Backup exportado!**.
- Se estiver usando a base fictícia, mantenha-a separada dos registros reais. **Importar backup substitui todos os lançamentos existentes**.
- Em **Mais → Pronto para trabalhar**, confira a permissão para a bolinha, a última data de backup e teste **Reativar bolinha**.
- Coloque o widget Primo na tela inicial. A bolinha deve ser ativada voluntariamente, sem inicialização automática após reiniciar o aparelho.
- No Redmi 13C, o sistema pode encerrar o serviço em segundo plano. Em **Dicas para manter ativa**, abra as configurações do aplicativo para conferir opções de bateria/autorização disponíveis. Isso não garante permanência.

## Fluxo diário (repetir por 7 dias)

1. Inicie a bolinha antes de trabalhar. Faça o registro só quando o veículo estiver parado, nunca dirigindo.
2. Registre corridas de **Rua** e, quando aplicável, **99** e **Uber Taxi**, variando Dinheiro, Pix, Cartão e App.
3. Registre gasolina e uma despesa de mecânico com observação de texto quando ocorrer.
4. Confira o botão **Resumo do dia**: contagem de corridas, valores recebidos, gasolina, mecânico e saldo parcial.
5. Toque em **Corrigir última** no resumo e altere apenas um dado de teste. Confira se o total é recalculado sem duplicar.
6. Experimente **Mais → Desfazer último lançamento**: escolha Cancelar e confirme que nenhum lançamento foi apagado. Em base de teste, confirme a exclusão para conferir o cálculo.
7. Alterne entre o Primo e 99, mapas, telefone ou WhatsApp. Observe se a bolinha continua visível; se sumir, reative-a pelo widget ou em **Mais**.
8. Consulte Histórico/Metas/Fechamento. Baixe e compartilhe CSV do período selecionado; confira o nome com datas e a separação de ganhos e gastos.
9. Faça um backup no fim do dia e confirme se **Último backup** mostra a hora correta. Não envie arquivos reais para repositórios públicos.

## Cenários de falha e recuperação

| Cenário | Resultado esperado |
|---|---|
| Android restringe a bolinha | O app continua abrindo; o usuário pode reativar manualmente |
| Falha ao iniciar foreground service | Não cria uma sobreposição sem serviço devidamente iniciado |
| Dia sem lançamento | Resumo tem zeros; não inventa corrida |
| Corrigir último lançamento do dia | O mesmo registro é editado, sem aumentar a quantidade |
| Cancelar desfazer | Nenhum dado removido |
| Exportação de backup cancelada | Não registra falsamente que houve backup |
| Exportação concluída | Data/hora do backup aparece em Mais |
| Importar outro backup | Dados restaurados; indicação de último backup exportado previamente é limpa |
| App reiniciado | Histórico, metas, widget e preferências continuam disponíveis |
| Atualizar APK assinado | Mantém registros e metas, sem desinstalar |

## Métricas sugeridas

Em cada dia anote: número de corridas, segundos aproximados por registro (meta: poucos toques), erros de valor registrados, quantas vezes a bolinha sumiu e se foi fácil reativá-la. Se algum item atrapalhar o uso, priorize simplicidade acima de novos recursos.

**Critério de decisão após 7 dias:** se o motorista consegue registrar sem ajuda, manter dados corretos e recuperar-se de falhas conhecidas, podemos avaliar disponibilizar uma versão estável. Ainda faltará avaliação em aparelho real e não podemos garantir que a MIUI/HyperOS nunca encerre o serviço.

**Privacidade:** dados do motorista ficam no aparelho e nos arquivos que ele exportar. Evite usar dados reais em bugs, prints públicos ou issues do GitHub.
