# Verificação de desenvolvimento

## Verificações antes de publicar

- Executar `tools/check.py` com JDK 17: política de versão, coerência do manifesto e pesquisa de dados sensíveis na fonte e no histórico.
- Compilar com Android platform 23 e build-tools 34.0.0; verificar a assinatura com apksigner, incluindo o esquema v1 para Android 6.
- Validar sincronização, apresentação horizontal/vertical e funcionamento offline num equipamento de teste.
- Validar mudança de álbum com limpeza das fotografias anteriores e a ação de apagar álbum/cache.
- Validar atualizações com e sem root, recusa de assinatura diferente e downgrade, e preservação da configuração.
- Na instalação root, verificar que a preparação usa uma pasta exclusiva e privada, removida após a instalação. Sem mktemp, a instalação deve falhar sem usar um caminho partilhado fixo.

Não publicar dados do equipamento, número de fotografias, ligações de álbuns ou detalhes pessoais nos resultados.

## Limites

A análise estática não substitui testes num equipamento. As alterações de segurança precisam de validação Android com e sem root antes de uma nova release. Os binários já publicados não são alterados por mudanças na fonte.
