# Primo 0.9.0 — Tutorial guiado do mascote

## Guia do aplicativo

Boas-vindas → Corrida → Gasolina → Mecânico → Metas → Editar meta → Histórico → Mais → Fechamento → Backup → Concluir.

O controle real fica realçado pelo efeito spotlight, mas **toques durante o guia não acionam ações reais**. O botão Próximo e o toque no destaque apenas mudam a etapa. Quando necessário o tutorial navega para a seção correspondente; **não cria, edita ou exclui registros**, não importa backup e não configura metas.

## Guia da bolinha

Boas-vindas → botão + → três opções → meta e X → arrastar simulado → Concluir. A simulação nunca inicia o serviço de sobreposição e não requer permissão.

## Descoberta e retorno

Na primeira abertura da 0.9.0 o usuário recebe um convite, que pode dispensar. Ao concluir o guia do aplicativo, pode escolher conhecer a bolinha. Ambos podem ser reabertos em **Mais → Aprenda com o Primo**.

## Validação em Android real

1. Instalar **por cima** da 0.8.0, preservando metas, históricos e backup. Não desinstalar.
2. Na primeira abertura aceitar o tutorial, testar Avançar, Voltar, Pular e toque no destaque.
3. Verificar se foco e balão são legíveis em Samsung Android 16 e Xiaomi Redmi 13C (fontes grandes, sem sobreposição de navegação, com recortes e rolagem).
4. Na etapa Metas, tocar no destaque e verificar que a tela de metas abre e o botão Editar fica destacado. Confirmar que valor da meta não muda.
5. Nas etapas Corrida, Gasolina, Mecânico e Backup, tocar no destaque e confirmar que **nenhum lançamento nem arquivo** é criado.
6. Concluir e iniciar o guia da bolinha. Testar menu, meta, X e arrastar o + simulado.
7. Verificar que a bolinha real e suas permissões não foram alteradas pelo tutorial.
8. Em Mais, reiniciar os dois tutoriais. Testar Pular e pressionar Voltar físico.
9. Reiniciar o aplicativo: o convite inicial não deve se repetir automaticamente; opção Mais continua acessível.
10. Regressão: salvar corridas, verificar meta, fechar bolinha, restaurar backup de testes, exportar CSV e atualizar o app sem perder dados.

## Política de conteúdo

Mensagens de fala são texto na tela (sem áudio). O mascote aparece em um círculo; as expressões são visuais. O tutorial é completamente offline.
