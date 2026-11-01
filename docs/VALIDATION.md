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

## Percurso com a release pública

- Release estável `v1.1.0` publicada em designerferro/frameo-icloud-photoframe, com APK, update.json e SHA256SUMS. O endpoint releases/latest devolveu a tag e os três assets esperados.
- A opção manual mostrou instalado 1.1.0 (2) e GitHub 1.1.0 (2), oferecendo reinstalação explícita da mesma versão.
- Reinstalação pelo próprio menu concluída com root; lastUpdateTime mudou e a aplicação retomou a apresentação com seis fotografias e o mesmo álbum.
- Para validar a subida automática, foi compilada **apenas localmente** uma variante 1.0.9 (versionCode 1), com o mesmo código e chave. Não foi publicada. O temporizador de consulta foi reiniciado, sem mudar o álbum, e a aplicação foi reaberta. Descarregou e instalou sozinha a release pública 1.1.0 (2), retomando as seis fotografias. A moldura ficou na release pública 1.1.0.
- O auxiliar setup.py também foi executado em macOS: reconheceu os pacotes existentes, descarregou Platform Tools oficial e verificou o checksum do índice Google.

A fonte associada à tag v1.1.0 é o commit da publicação inicial. Este registo posterior documenta o teste que depende de a release já estar publicada; não altera o APK dessa tag.
