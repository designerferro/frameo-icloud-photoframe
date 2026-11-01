# Photoframe — iCloud numa moldura Android

Autor técnico do código e do APK: **Codex**. Publicação e manutenção: **[designerferro](https://github.com/designerferro)**.

[Descarregar a última release](https://github.com/designerferro/frameo-icloud-photoframe/releases/latest) · [Prompt de recriação](PROMPT.md) · [Compilar manualmente](docs/BUILD-MANUAL.md) · [Histórico](CHANGELOG.md)

Aplicação Android nativa, leve, para apresentar fotografias de um **Álbum partilhado público iCloud**. Compatível com Android 6/API 23 ou superior. Uma referência de compatibilidade é a Apveelveer AVE-107/RK312x; não há um método universal de desbloqueio para outras molduras. O funcionamento de arranque em versões Android mais recentes pode depender das restrições do fabricante.

O APK das Releases é genérico: **não contém fotografias, ligação a um álbum, credenciais Apple, token GitHub nem chave privada**. A ligação é introduzida na moldura e guardada no espaço privado da aplicação.

As melhorias de segurança e limpeza de dados na branch `main` destinam-se à próxima versão. O APK da release `v1.1.0` mantém o comportamento original até ser publicada uma nova release validada.

## Fotografias e apresentação

- Mudança a cada 15 segundos; toque avança.
- Toque prolongado, ou Back, abre o menu.
- Sincronização ao abrir e a cada 15 minutos enquanto a apresentação está em primeiro plano; em falha, tenta de novo em 1 minuto.
- Menu para apagar a ligação do álbum e todas as fotografias locais; ao mudar de álbum, a limpeza anterior está selecionada por defeito.
- Cache privada para apresentar as fotografias sem internet. Se a sincronização falhar ou o álbum estiver vazio, mantém a última cópia.
- Sem barras pretas acrescentadas pelo layout: fotografia inteira sobre fundo suave da própria imagem, ou preenchimento com recorte. O primeiro plano mantém a proporção.
- Orientação automática, horizontal ou vertical. A opção manual é útil em molduras sem sensor de rotação.
- Arranque via BOOT_COMPLETED e suporte como aplicação HOME no Android 6.

## Criar o álbum

Na aplicação Fotografias Apple, criar um **Álbum partilhado**, adicionar imagens e ativar **Site público** nas opções do álbum. Usar uma ligação `https://www.icloud.com/sharedalbum/#...`. Uma ligação temporária `share.icloud.com/photos/...` ou `icloudlinks` não é equivalente.

Qualquer pessoa que obtenha a ligação pública pode ver as fotografias: usar um álbum dedicado. [Instruções Apple](https://support.apple.com/en-gb/guide/icloud/mme985ae44d8/icloud).

## Instalar o APK já compilado

Não é necessário compilar para usar a aplicação. Descarregar o APK em [Releases](https://github.com/designerferro/frameo-icloud-photoframe/releases/latest). A mesma página contém `update.json` e `SHA256SUMS`.

1. Instalar [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) para o sistema do computador e adicionar a pasta ao PATH. Em Windows, pode ser necessário o controlador USB fornecido pelo fabricante. Em GNU/Linux, pode ser necessário configurar permissões USB/udev; não correr o ADB permanentemente como root para contornar isso.
2. Ligar a moldura por USB, com ADB já disponível e autorizado.
3. Confirmar e instalar, substituindo o nome do ficheiro se a versão for diferente:

```text
adb devices -l
adb install Photoframe-v1.1.0.apk
adb shell am start -n pt.photoframe/.MainActivity
```

Se já existir uma versão assinada com a mesma chave, usar `adb install -r ...` para preservar dados. Se houver vários dispositivos, usar `adb -s NUMERO_DO_DISPOSITIVO`.

4. No primeiro arranque, introduzir a ligação pública do álbum. O menu continua acessível mesmo antes de descarregar fotografias. Wi-Fi e hora corretos são necessários para HTTPS.
5. Confirmar que a aplicação funciona **antes** de desativar o Frameo.

### Substituir o Frameo de forma reversível

Este projeto não obtém root nem disponibiliza firmware. É necessário um caminho de recuperação conhecido. Guardar os backups e informações específicos do equipamento. Não fazer flash de uma ROM apenas porque partilha o SoC.

Confirmar root e o pacote Frameo:

```text
adb shell "su -c id"
adb shell pm list packages frameo
```

O pacote Frameo pode ser `net.frameo.frame`. Se for diferente, adaptar. Para comandos com argumentos dentro de `su -c`, as aspas seguintes preservam a linha no Android tanto em PowerShell como em shells POSIX:

```text
adb shell "su -c 'pm disable-user --user 0 net.frameo.frame'"
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

Se aparecer um seletor, escolher Photoframe e Sempre. Reiniciar com `adb reboot`. Depois de configurada, a moldura funciona sem computador/USB; precisa apenas de alimentação e Wi-Fi para receber novidades.

### Reativar o Frameo

```text
adb shell "su -c 'pm enable --user 0 net.frameo.frame'"
adb shell am force-stop pt.photoframe
adb shell monkey -p net.frameo.frame -c android.intent.category.LAUNCHER 1
```

Depois de o Frameo abrir, desativar o substituto se se pretende regressar ao comportamento anterior:

```text
adb shell pm disable-user --user 0 pt.photoframe
adb reboot
```

Para reativar Photoframe: `adb shell pm enable --user 0 pt.photoframe`.

## Atualizações do APK

No menu de atualização das fotografias aparecem:

- Versão e `versionCode` instalados.
- Última release estável consultada no GitHub.
- **Procurar / forçar atualização do APK**: ignora o intervalo automático. Se já estiver instalada a mesma release, permite confirmar uma reinstalação.
- Opção para ligar/desligar as atualizações automáticas do APK.

A verificação automática ocorre ao abrir, respeitando um intervalo de 6 horas, e a cada 6 horas enquanto a apresentação está aberta. Não é um serviço de segundo plano nem atualiza com a moldura desligada. Com root, o APK verificado é preparado numa pasta exclusiva e privada, removida após `pm install -r`; é necessário `mktemp` no Android. Sem root, abre a confirmação normal do instalador Android. Não se contornam autorizações do sistema.

Origem: `https://api.github.com/repos/designerferro/frameo-icloud-photoframe/releases/latest`. A aplicação valida release estável, nome/tag/repositório, SHA-256, identidade interna do pacote, versão e assinatura. Não permite downgrade nem atualização por uma chave diferente. O ContentProvider de instalação só partilha o APK verificado, com uma autorização temporária de leitura.

As versões são comparadas pelo inteiro **versionCode**. `versionName` e tag são apresentados ao utilizador. A última release guarda um `update.json` semelhante a:

```json
{
  "versionName": "1.1.0",
  "versionCode": 2,
  "repository": "designerferro/frameo-icloud-photoframe",
  "author": "Codex",
  "apk": "Photoframe-v1.1.0.apk",
  "sha256": "DIGEST_SHA256_GERADO_A_PARTIR_DO_APK",
  "minSdk": 23
}
```

## Preparar o computador: Windows, Debian/Ubuntu e macOS

Requisitos comuns: **Python 3.9+**, **JDK 17**, Android SDK **platform 23** e **build-tools 34.0.0**. Git é opcional para descarregar o código; o ZIP da release também serve. Não é necessário Gradle nem Android Studio.

| Sistema | Preparação |
|---|---|
| GNU/Linux baseado em Debian, x86_64 | Instalar Python 3, Git e unzip através do gestor da distribuição, por exemplo `sudo apt install python3 git unzip`. Instalar um JDK 17 disponível na distribuição ou o [Temurin 17 oficial](https://adoptium.net/temurin/releases/?version=17). As ferramentas nativas Android usadas aqui são para Linux x86_64. |
| Windows x64 | Instalar Python 3 e Temurin 17 dos fornecedores oficiais. Escolher instalação de Python com launcher `py`. Extrair os ficheiros para um caminho local. Usar PowerShell. |
| macOS Intel ou Apple Silicon | Instalar Python 3 e Temurin 17 adequados à arquitetura. Algumas ferramentas Android oficiais são Intel; o macOS pode pedir Rosetta no Apple Silicon. |

Downloads: [Python](https://www.python.org/downloads/) · [Temurin 17](https://adoptium.net/temurin/releases/?version=17) · [Android SDK](https://developer.android.com/tools).

Nos exemplos, **`PY` significa o comando Python 3**: `python3` em GNU/Linux/macOS e `py -3` em Windows. Substituir literalmente esse prefixo; não é uma variável de ambiente.

### Preparação com auxiliar Python

Na raiz do código:

```text
PY tools/setup.py --sdk "CAMINHO_ABSOLUTO_DO_SDK"
PY tools/build.py --sdk "CAMINHO_ABSOLUTO_DO_SDK" --jdk "CAMINHO_ABSOLUTO_DO_JDK_17" --out "PASTA_DE_SAIDA_FORA_DO_REPOSITORIO"
```

O auxiliar descarrega arquivos oficiais Google, verifica os checksums do índice oficial e organiza as pastas. Não altera variáveis globais nem aceita licenças automaticamente. Consultar as licenças dos fornecedores. O build pede a palavra-passe da chave local e cria-a se não existir.

### Preparação manual, sem executar o auxiliar

1. Instalar Python 3 e JDK 17. Confirmar `PY --version`, `java -version` e `javac -version`.
2. Descarregar [platform 23](https://dl.google.com/android/repository/platform-23_r03.zip).
3. Descarregar build-tools 34 para o computador: [GNU/Linux](https://dl.google.com/android/repository/build-tools_r34-linux.zip), [Windows](https://dl.google.com/android/repository/build-tools_r34-windows.zip) ou [macOS](https://dl.google.com/android/repository/build-tools_r34-macosx.zip).
4. Extrair a pasta `android-6.0` do primeiro ZIP para `SDK/platforms/android-23`. Dentro dessa pasta deve existir `android.jar`.
5. Extrair a pasta raiz do segundo ZIP para `SDK/build-tools/34.0.0`. Dentro devem existir `aapt`/`aapt.exe`, `zipalign`/`zipalign.exe`, `lib/d8.jar` e `lib/apksigner.jar`.
6. Extrair Platform Tools para `SDK/platform-tools` caso se queira usar ADB. As ferramentas que compilam e as ferramentas que comunicam com a moldura são distintas.
7. Passar os caminhos absolutos com `--sdk` e `--jdk`, como acima. Alternativamente, o build reconhece `ANDROID_SDK_ROOT`/`ANDROID_HOME` e `JAVA_HOME`.

Também é possível usar `sdkmanager` oficial para instalar `"platforms;android-23" "build-tools;34.0.0" "platform-tools"`, respondendo às licenças na própria ferramenta. Para compilar **sem o script de build**, o procedimento completo com comandos POSIX e PowerShell está em [docs/BUILD-MANUAL.md](docs/BUILD-MANUAL.md).

### Assinatura e ficheiros gerados

O build gera fora da árvore do código:

- `Photoframe-vX.Y.Z.apk`, assinado e verificado.
- `update.json` e `SHA256SUMS`.
- Chave local `photoframe-signing.p12`, se ainda não existir.

Para uma chave existente: `--keystore CAMINHO --alias photoframe`. Em automação, a palavra-passe pode ser fornecida pela variável `PHOTOFRAME_KEYSTORE_PASSWORD`. Nunca escrever chaves ou palavras-passe no GitHub.

**Uma compilação própria tem a sua assinatura.** Não pode ser instalada por cima de uma release oficial assinada com outra chave, nem receber atualizações oficiais automaticamente por cima. Para manter a continuidade, usar sempre a mesma chave e, num fork, adaptar a origem das atualizações. Preservar uma cópia segura da chave de publicação; não está incluída no repositório.

## Publicar uma nova release

1. Alterar `versionName` e aumentar `versionCode` em `VERSION.json` e `AndroidManifest.xml`.
2. Atualizar `CHANGELOG.md` e `docs/RELEASE-NOTES.md`.
3. Executar `PY tools/check.py --jdk CAMINHO_DO_JDK` e compilar com a mesma chave das releases anteriores.
4. Fazer commit do código. Não usar `git add .` num diretório com ficheiros pessoais: adicionar explicitamente os ficheiros de fonte.
5. Criar uma release **estável**, tag `vX.Y.Z`, e anexar APK, `update.json` e `SHA256SUMS`. O GitHub inclui os arquivos do código-fonte correspondentes à tag.

Com [GitHub CLI](https://cli.github.com/) autenticado, depois de fazer push do commit:

```text
PY tools/release.py --out "PASTA_DA_COMPILACAO"
```

Ou fazer a operação manualmente em GitHub → Releases → Draft a new release, escolher a tag/commit e anexar apenas os três ficheiros públicos. Publicar como release estável para aparecer em `/releases/latest`. A chave privada nunca é enviada por esse script.

Não existe `.gitignore`, por pedido do responsável pelo projeto. SDK, chaves e diretórios de saída devem ficar fora deste repositório. O [prompt](PROMPT.md) descreve as funcionalidades e o contexto de recriação.

## Limitações

Não é uma aplicação oficial Apple/Frameo e não substitui a atualização do Android. A sincronização usa os endpoints do site público iCloud, que podem mudar. Vídeos não são reproduzidos. Um álbum vazio conserva a última cópia local. Não faz flash de firmware. minSdk 23 não garante instalação em todo o Android recente: alguns sistemas bloqueiam aplicações com targetSdk antigo ou restringem arranque/instalação. O alvo principal deste projeto continua a ser Android 6. Para detalhes técnicos da versão e verificações de desenvolvimento, ver [VALIDATION.md](docs/VALIDATION.md).
