# Contribuir para o Photoframe

Podes ajudar com relatos de problemas, sugestões, documentação ou alterações de código. Usa português ou inglês e mantém uma conversa respeitosa e centrada no assunto.

## Relatar problemas ou sugerir melhorias

Abre [uma nova issue](https://github.com/designerferro/frameo-icloud-photoframe/issues/new/choose) e escolhe **Relatar um problema**, **Sugerir uma melhoria** ou **Colocar uma dúvida**. Pesquisa primeiro nas issues abertas e fechadas; se já existir uma semelhante, acrescenta informação nessa conversa. Usa uma issue por assunto.

Nos problemas, indica versão do Photoframe, modelo da moldura, Android, passos para reproduzir e resultado esperado/observado. Se não souberes algum dado, diz isso. Nas melhorias, explica a necessidade e dá um exemplo do comportamento desejado. Não precisas de saber programar.

As issues são públicas. Não publiques ligações a álbuns iCloud, fotografias pessoais, palavras-passe, tokens, chaves de assinatura, números de série ou dados da tua rede. Revê capturas e registos antes de os anexares.

## Acompanhamento

Os formulários aplicam `bug` (problema), `enhancement` (melhoria) ou `question` (dúvida), juntamente com `needs-triage` (por analisar). Na análise, o responsável pode pedir informação adicional, retirar `needs-triage` ou marcar duplicados com uma ligação à issue original. Issues encerradas não significam necessariamente que houve uma nova versão: consulta o motivo de encerramento e as notas das releases.

As propostas são avaliadas conforme a utilidade, compatibilidade com Android 6, privacidade, recursos das molduras e esforço de manutenção. Abrir uma issue não garante implementação nem prazo de resposta. A reação 👍 ajuda a sinalizar interesse sem repetir comentários.

## Propor código ou documentação

Para alterações maiores, abre primeiro uma proposta para discutir o comportamento e a compatibilidade. Para correções pequenas ou documentação, podes abrir diretamente um pull request a partir de um fork, dirigido ao ramo `main`.

Explica o problema, o que muda e como verificaste o resultado. Liga a issue relacionada, quando existir. Mantém cada pull request centrado num assunto e preserva o funcionamento no Android 6/API 23. Consulta o [README](README.md) e o [guia de compilação](docs/BUILD-MANUAL.md) para preparar as ferramentas.

Para código, executa `python3 tools/check.py --jdk CAMINHO_DO_JDK_17`, `python3 -m unittest discover -s tests -p 'test_*.py'` e `node --test tests/remote-api.test.js`, além das verificações adequadas à alteração. Em Windows, podes usar `py -3` em vez de `python3`. Indica se não pudeste testar num equipamento real. Para documentação, confirma os links e o texto alterado.

Nunca incluas SDK, APK, ficheiros pessoais ou chaves no código-fonte. Uma compilação própria tem a sua assinatura; a chave oficial e a publicação de releases são geridas pelo responsável do projeto. Deixa alterações de versão e publicação para a preparação de uma release.
