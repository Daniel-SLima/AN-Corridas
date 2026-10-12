# Primo 0.9.1 — QA visual de acabamento

## Objetivo

Evitar um clarão branco de um frame no canto superior esquerdo durante o tutorial e deixar as superfícies e ações visualmente consistentes nas quatro abas, sem modificar comportamento financeiro.

## Regressão do spotlight

1. Com o Primo 0.9.1 instalado, abra **Mais → Aprenda com o Primo → Tutorial do aplicativo**.
2. Pressione Próximo várias vezes e observe o canto superior esquerdo, sobretudo na transição entre **Histórico → Mais**, **Metas → Editar**, **Fechamento → Backup**.
3. Volte etapas repetidas vezes. Não deve surgir cartão branco no canto, nem destacar algo fora da tela.
4. Com fonte grande nas configurações Android, repita. O balão deve ficar legível, sem bloquear os controles de navegação; o foco deve acompanhar a rolagem.
5. Na simulação da bolinha, teste +, três atalhos, meta e arraste; sem ativar uma sobreposição de verdade.
6. Confira que toques no destaque apenas avançam o tutorial e nunca salvam/apagam registros.

## Padronização de interface

- Superfícies principais: branco, cantos de 18 dp, borda suave cinza-azulada e padding uniforme.
- Títulos de seção: 19 sp, negrito, texto escuro.
- Ações dentro dos cartões: altura mínima 52 dp, botão primário azul e secundário azul-claro, ícone à esquerda.
- As opções do fechamento continuam num trio compacto (Ver/Baixar/Compartilhar).
- Os botões semânticos de cadastro Corrida/Gasolina/Mecânico mantêm suas cores de reconhecimento.
- Seções de Mais: Fechamento, Corrigir registros, Bolinha e tela inicial, Metas e preferências, Aprenda com o Primo, Segurança dos dados.
- Em Início, Histórico, Metas e Fechamento, verificar cartões de resumo, listagem e metas com o mesmo acabamento.

## Dados e segurança

- A atualização sobre a versão 0.9.0 **não apaga dados**; manter a assinatura de release fixa.
- Não mudar o banco nem os arquivos JSON/CSV.
- Testar em Samsung e no Redmi 13C quando possível; compilação e testes JVM não validam visual em dispositivo.
