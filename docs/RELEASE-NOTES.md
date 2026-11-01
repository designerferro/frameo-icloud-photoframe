Photoframe 1.3.3 — correção da instalação automática com root (versionCode 9).

Todo o código foi escrito pelo Codex. Publicação: designerferro.

- Corrige INSTALL_FAILED_INVALID_URI no Android 6 ao instalar um APK numa pasta privada.
- Envia o APK verificado diretamente para uma sessão do instalador Android, sem o tornar público.
- Confirma a conclusão da sessão e abandona sessões que falhem.
- Mantém o estado da operação quando se repete um pedido e confirma a instalação após atualizar.
- Inclui a correção do login HTTP em molduras mais lentas, da versão 1.3.2.

Preserva álbum, fotografias, filmes, configurações e credenciais. Mantém a assinatura anterior.

Se o atualizador de uma versão anterior falhar, instalar este APK com adb install -r Photoframe-v1.3.3.apk. Não desinstalar a aplicação nem apagar dados.

APK: Photoframe-v1.3.3.apk, update.json e SHA256SUMS.
