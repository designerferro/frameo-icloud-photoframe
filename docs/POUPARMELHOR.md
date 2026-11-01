**Dar nova vida a uma moldura Frameo: fotografias do iCloud, com atualização automática**

Quem tem uma moldura digital Android antiga pode conseguir reaproveitá-la para apresentar fotografias de um álbum partilhado do iCloud, sem manter um computador ligado.

A solução chama-se **Photoframe**. O APK foi desenvolvido pelo **Codex**, e o código, o prompt para recriar o projeto e as instruções estão na conta **designerferro** do GitHub:

- [Código e guia completo](https://github.com/designerferro/frameo-icloud-photoframe)
- [Descarregar o APK mais recente](https://github.com/designerferro/frameo-icloud-photoframe/releases/latest)
- [Prompt de recriação](https://github.com/designerferro/frameo-icloud-photoframe/blob/main/PROMPT.md)

### O que faz

Mostra fotografias em ecrã inteiro, muda de imagem a cada 15 segundos e procura novidades no álbum a cada 15 minutos. Guarda uma cópia na própria moldura, pelo que continua a mostrar as fotografias quando não há internet.

Para preencher o ecrã, permite escolher entre fotografia inteira sobre um fundo suave da própria imagem e preenchimento com recorte. Funciona na horizontal e na vertical, com opção para fixar a orientação quando a moldura não tem sensor de rotação.

Um toque avança. Um toque prolongado abre o menu: atualizar fotografias, configurar o álbum, escolher a apresentação e consultar ou atualizar o APK.

### Antes de começar

O projeto destina-se a Android 6 ou superior e foi pensado inicialmente para a Apveelveer AVE-107 com Rockchip RK312x. Para substituir o Frameo, é necessário acesso ADB e root já disponíveis, além de um caminho de recuperação conhecido.

Não existe aqui um método universal para desbloquear todas as molduras. O acesso depende do fabricante e do firmware. Guardem os backups e não instalem uma ROM de outro aparelho apenas por ter o mesmo processador. O procedimento abaixo não exige flash de firmware.

### 1. Criar um álbum dedicado

Na aplicação Fotografias Apple, criar um **Álbum partilhado**, por exemplo “Moldura”, e adicionar as fotografias desejadas. Nas opções do álbum, ativar **Site público** e copiar a ligação.

A ligação deve ser do tipo:

```text
https://www.icloud.com/sharedalbum/#...
```

Não confundir com uma ligação temporária criada ao selecionar fotografias e escolher “Copiar ligação iCloud”.

**Quem tiver a ligação pública pode ver as fotografias.** Convém usar um álbum dedicado e não publicar essa ligação. A aplicação não pede a palavra-passe Apple.

### 2. Preparar o computador

Pode ser usado **Windows, GNU/Linux baseado em Debian ou macOS**. Para instalar o APK já disponível, basta preparar as [Android Platform Tools oficiais](https://developer.android.com/tools/releases/platform-tools), que incluem o ADB, e usar um cabo USB de dados.

Ligar a moldura, autorizar a ligação ADB quando necessário e confirmar:

```text
adb devices -l
```

A moldura deve aparecer como `device`. Se aparecer `unauthorized`, falta autorizar a ligação; se não aparecer, verificar o cabo, as permissões USB e, no Windows, o controlador do fabricante.

Os comandos seguintes pressupõem apenas um dispositivo Android ligado. Se houver vários, usar `adb -s NUMERO_DO_DISPOSITIVO`.

### 3. Descarregar e instalar o APK

Abrir a página de [Releases do projeto](https://github.com/designerferro/frameo-icloud-photoframe/releases/latest) e descarregar o ficheiro `Photoframe-vX.Y.Z.apk`.

No Terminal ou PowerShell, dentro da pasta onde ficou o ficheiro, executar, adaptando o nome à versão descarregada:

```text
adb install Photoframe-v1.1.0.apk
adb shell am start -n pt.photoframe/.MainActivity
```

Para atualizar uma instalação existente assinada com a mesma chave, usar `adb install -r`.

Na moldura, introduzir a ligação pública do álbum. O APK distribuído é genérico: não inclui fotografias nem a ligação ao álbum de outra pessoa. Essa configuração fica guardada na própria moldura.

O Wi‑Fi e a hora têm de estar corretos para a ligação segura ao iCloud. Um toque prolongado permite consultar o estado e pedir uma atualização imediata.

### 4. Substituir o Frameo sem apagar os seus dados

Só avançar quando o Photoframe já estiver a apresentar as fotografias.

Confirmar o acesso root e o nome do pacote:

```text
adb shell "su -c id"
adb shell pm list packages frameo
```

Se o pacote for `net.frameo.frame`, desativá-lo e abrir o ecrã principal:

```text
adb shell "su -c 'pm disable-user --user 0 net.frameo.frame'"
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

Se aparecer um seletor, escolher **Photoframe → Sempre**. Reiniciar com:

```text
adb reboot
```

Depois de configurada, a moldura pode funcionar sem USB nem computador: apenas alimentação e Wi‑Fi para receber novidades. Se a rede ainda não estiver pronta ao ligar, apresenta a cópia local e volta a tentar a sincronização.

### 5. Atualizar a aplicação

No menu de toque prolongado aparecem a **versão instalada** e a **última release consultada no GitHub**. A aplicação verifica novas versões automaticamente a cada seis horas enquanto está aberta.

Com root, pode instalar a atualização automaticamente. Sem root, é necessária a confirmação do instalador Android. Antes de instalar, verifica o SHA‑256, o pacote, a versão e a assinatura do APK. Não faz downgrade.

A opção **“Procurar / forçar atualização do APK”** verifica imediatamente e permite reinstalar a mesma release. As atualizações preservam o álbum e as fotografias locais quando a assinatura se mantém.

### 6. Voltar ao Frameo

Para reativar o Frameo:

```text
adb shell "su -c 'pm enable --user 0 net.frameo.frame'"
adb shell am force-stop pt.photoframe
adb shell monkey -p net.frameo.frame -c android.intent.category.LAUNCHER 1
```

Depois de o Frameo abrir, pode desativar-se o substituto:

```text
adb shell pm disable-user --user 0 pt.photoframe
adb reboot
```

### Para quem quiser compilar o próprio APK

O [README do GitHub](https://github.com/designerferro/frameo-icloud-photoframe#preparar-o-computador-windows-debianubuntu-e-macos) explica a preparação em Windows, GNU/Linux e macOS. Há um auxiliar Python e instruções manuais, incluindo a compilação sem o auxiliar.

São usados Python 3, JDK 17 e ferramentas oficiais Android. O [prompt incluído no código](https://github.com/designerferro/frameo-icloud-photoframe/blob/main/PROMPT.md) descreve o que pedir ao Codex para recriar e manter a aplicação.

Uma compilação própria deve preservar a sua chave de assinatura para futuras atualizações. Não se deve publicar essa chave. APK assinados com chaves diferentes não se atualizam por cima uns dos outros.

### Limitações a conhecer

Esta solução apresenta fotografias, não vídeos. Não é uma aplicação oficial Apple ou Frameo. A sincronização depende dos pedidos usados pelo site público do iCloud; alterações nesse serviço podem obrigar a atualizar o código. Também não transforma um Android antigo num sistema atualizado.

É uma forma de voltar a dar uso a uma moldura que já temos, com controlo sobre o código e um caminho para regressar ao Frameo.
