# Verificação de desenvolvimento

Este registo técnico acompanha o código; não faz parte do artigo Pouparmelhor.

## Verificações locais — 2026-11-01

- Compilação com Temurin JDK 17, Android platform 23 e build-tools 34.0.0 em macOS Apple Silicon.
- Assinatura do APK verificada por apksigner, incluindo esquema v1 necessário para Android 6.
- Testes de política de versão: versão superior, recusa de downgrade mesmo em modo forçado, reinstalação da mesma versão apenas por ação manual, versões inválidas, forma de SHA-256 e rejeição de nome de versão com caminho.
- Fonte pública sem `.gitignore`, chave de assinatura, álbum ou fotografias pessoais. APK público sem assets pessoais.
- Instalação sobre a versão local 1.0 preservou o pacote e as fotografias. A configuração do protótipo com álbum embutido foi migrada separadamente no equipamento, sem a incluir na release.
- AVE-107/Android 6.0.1: sincronização direta com seis fotografias e menu com versão instalada, autoria e ação de atualização do APK.
- Apresentação vertical por opção manual observada com fundo suave em vez de barras pretas; configuração horizontal reposta após a verificação.

O auxiliar é portável por seleção do arquivo oficial de cada sistema e uso de subprocessos sem shell. Execução nativa Windows e GNU/Linux não foi realizada nesta sessão; os passos e comandos manuais estão documentados para esses sistemas. O instalador Android sem root também requer verificação num equipamento sem root.

As verificações de atualização pela release real serão acrescentadas quando os assets estiverem publicados. Não é possível concluir esse percurso antes de existir a origem de atualização.
