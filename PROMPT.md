# Prompt para recriar e manter o APK

Autor técnico do código e do APK: **Codex**. Nome do utilizador GitHub: **designerferro**. Definir `GITHUB_USER` com este nome e `GITHUB_REPOSITORY` como `GITHUB_USER/frameo-icloud-photoframe`; nas restantes instruções usar estas referências e adaptar as ligações e os metadados se o utilizador ou o nome do repositório mudar.

Este texto permite pedir a recriação funcional do projeto. Para refazer esta versão a partir do código existente, seguir o README e as instruções de compilação; um modelo não garante gerar código ou binários idênticos apenas a partir do prompt.

## Pedido de implementação

Cria uma aplicação Android Java nativa, leve, chamada Photoframe, package `pt.photoframe`, compatível com Android 6/API 23 e sem Google Play Services, AndroidX ou WebView. Uma referência de compatibilidade é a Apveelveer AVE-107 Rockchip RK312x, mas deve funcionar com fotografias e ecrã em orientação horizontal ou vertical.

O APK público deve ser genérico: nunca incluir um álbum pessoal, fotografias de exemplo pessoais, palavras-passe Apple, tokens GitHub ou chaves de assinatura. Pedir no próprio ecrã a ligação Site público de um Álbum partilhado iCloud (`https://www.icloud.com/sharedalbum/#TOKEN`). Guardar a configuração em SharedPreferences privados e validar esquema, host, caminho e formato do token. Explicar que quem possuir essa ligação pode ver as fotografias. Nunca preencher o campo com a ligação já guardada nem mostrar o URL/token atual no menu. Apresentar apenas a indicação de que existe um álbum configurado. O campo para substituir a ligação deve iniciar vazio, mascarar a entrada e não oferecer opção de revelar; guardar sem introduzir uma nova ligação deve manter o álbum e os ficheiros locais.

Sincronizar diretamente com os endpoints utilizados pelo site público iCloud (`webstream` e `webasseturls`), sem login Apple. Derivar a partição do token base62 conforme o site Apple. Estes endpoints não são uma API pública garantida: tratar falhas e manter cópia local. Apresentar fotografias e filmes compatíveis; preparar MP4/H.264 adaptado ao ecrã e ao codificador da moldura, com áudio AAC, duração de 30 segundos por omissão e volume de 0 a 10. Ajustar JPEG a até 1280×1280, respeitar todas as orientações EXIF e guardar no espaço privado da aplicação. Publicar o novo manifesto de cache apenas após descarregar todas as imagens necessárias; em falha ou álbum vazio conservar o último conjunto. Sincronizar ao abrir e a cada 15 minutos em primeiro plano; repetir em 1 minuto se falhar. Não pedir permissão para armazenamento partilhado.

Mostrar fotografias a cada 15 segundos; toque avança. Remover barras pretas tanto na horizontal como na vertical: por defeito, foto completa sobre fundo suave e escurecido derivado da própria foto, com opção adicional CENTER_CROP para preencher recortando margens sem distorcer o primeiro plano. Manter o ecrã ligado. Dar opções de orientação automática/fullSensor, horizontal e vertical, persistidas. Toque prolongado ou botão Back abre um menu em português com estado, atualização das fotos, configuração do álbum, apresentação, definições Android e atualização do APK.

Implementar atualizações usando a última release estável de `GITHUB_REPOSITORY`. Mostrar no mesmo menu versão instalada e última release consultada. Comparar `versionCode`, não cadeias de versão; não permitir downgrade. Verificar automaticamente ao abrir, respeitando intervalo de 6 horas, e periodicamente enquanto a aplicação estiver em primeiro plano. Permitir desativar a atualização automática. A ação manual ignora o intervalo e permite forçar reinstalação da mesma release após confirmação.

Cada release contém `Photoframe-vX.Y.Z.apk`, `update.json` com versionName/versionCode/repository/author/apk/sha256/minSdk e `SHA256SUMS`. Validar repositório, release estável, tag, nome de asset, HTTPS, limites de tamanho, digest SHA-256, package name, versão interna e assinatura igual à aplicação instalada. Nunca desativar validação TLS nem usar credenciais GitHub na aplicação. Não instalar APK com assinatura diferente ou versão inferior. Usar `su -c` e `pm install -r` se existir root; caso contrário partilhar apenas o APK verificado com o instalador Android através de ContentProvider privado com concessão temporária de leitura. Não conceder acesso a outras pastas. Retomar a apresentação após MY_PACKAGE_REPLACED. Suportar BOOT_COMPLETED e o papel de HOME no Android 6.

Preparar fonte para GitHub na conta indicada por `GITHUB_USER`, com autoria técnica atribuída a Codex e este prompt incluído. Criar um `.gitignore` local, incluindo nele a entrada `.gitignore` para que o próprio ficheiro seja ignorado. Não adicionar esse ficheiro ao Git nem publicá-lo no GitHub. Verificar que não está no índice Git, sem proibir a sua existência local. Todas as ferramentas, ficheiros de compilação e chaves devem ficar fora da árvore do código. Fornecer scripts Python 3 portáveis para preparar o SDK e compilar com JDK 17, Android platform 23 e build-tools 34.0.0. Usar aapt/javac/D8/zipalign/apksigner; tratar os executáveis e separadores de caminho de Windows, GNU/Linux e macOS. Verificar assinatura e gerar metadados/checksum da release. Nunca publicar a chave privada.

O README deve explicar instalação do APK e recuperação reversível do Frameo, requisitos e preparação em Windows, distribuições GNU/Linux baseadas em Debian e macOS, via auxiliar e manualmente, incluindo a compilação manual. Documentar chaves locais versus chave das releases e o facto de uma compilação assinada por outra chave não aceitar atualizações oficiais por cima. Incluir instruções de publicação de releases sem chaves nem dados pessoais. Incluir verificações de rejeição de downgrade, reinstalação automática indevida e metadados inválidos.

Preparar também um artigo em português europeu para o Pouparmelhor, com ligações para o repositório e Releases, passos práticos e sem informação sobre testes. Distinguir a instalação de um APK já disponível da compilação opcional. Não sugerir flash de uma ROM apenas por partilhar o SoC. Não assumir um método universal para obter ADB/root.


## Versão 1.2.0

Todo o código é escrito pelo Codex; destacar isto no README e na página GitHub. Reproduzir filmes do álbum com cache offline, preparar MP4/H.264 na moldura e reduzir a resolução ao ecrã quando necessário. Conservar áudio AAC. Permitir configurar duração (30 segundos por omissão, máximo calculado pelo armazenamento da moldura) e volume entre 0 e 10 (silêncio a máximo). Ignorar filmes incompatíveis com indicação no estado e manter fotografias utilizáveis.


## Versão 1.3.0 — acesso remoto e ordem

Implementar uma página HTML/CSS/JavaScript leve incluída no APK, sem recursos externos, servida por HTTP na porta 8080 para clientes da rede interna. Não incluir integração Home Assistant. Manter o servidor num serviço Android não exportado, em primeiro plano com notificação, independente da atividade. Mostrar o endereço e permitir ativação/desativação e recuperação de credenciais no menu local.

Credenciais iniciais: admin / admin. O primeiro login só permite escolher um novo utilizador diferente de admin e uma nova palavra-passe antes de consultar/alterar configurações. Guardar palavra-passe como PBKDF2 com salt aleatório; usar sessões aleatórias, expiração, cookie HttpOnly/SameSite, invalidação após mudança, limites de tentativas, validação Host/Origin, proteção CSRF e pedidos de tamanho limitado. Não registar credenciais nem o URL/token do álbum. Avisar que HTTP não cifra a ligação e destina-se à LAN.

A página deve expor as configurações e ações do menu: álbum apenas para substituição (campo vazio e mascarado), remoção local com confirmação, sincronização, apresentação/recorte, orientação, duração/volume dos filmes, atualização automática, procura/forçar reinstalação, abertura das definições Android na moldura e ordem. A instalação sem root continua sujeita à confirmação local do Android. Permitir mudar as credenciais e terminar a sessão. As credenciais podem ser repostas localmente sem apagar o álbum.

Acrescentar Ordem de apresentação aos dois interfaces, com Aleatório e Por data da fotografia, persistente e aplicável a fotografias/filmes offline. Por data é a ordem ascendente pela data fornecida pelo iCloud e a opção por omissão. Aleatório baralha voltas completas e evita repetição imediata entre voltas. Guardar a data no manifesto da cache, aceitar o formato antigo e conservar a sequência quando a sincronização não alterar os itens.


## Versão 1.3.1 — preparação MP4 compatível

Intercalar os dados de áudio e vídeo ao escrever MP4, tanto na cópia direta como na conversão: não gravar uma faixa inteira antes da outra, pois o gravador nativo pode bloquear. Em Android anterior a API 25, detetar amostras de vídeo com timestamps de apresentação reordenados (B-frames) antes de iniciar a gravação direta e converter esses filmes para H.264 sem B-frames. A limitação é documentada em https://developer.android.com/reference/android/media/MediaMuxer . Manter o áudio AAC, os limites de duração e resolução. Atualizar o estado durante consulta/download/preparação e distinguir a tentativa atual do erro anterior. Validar filmes sintéticos longos, áudio como primeira faixa e H.264 com/sem B-frames, sem incluir ficheiros pessoais no código ou releases.

## Versão 1.3.2 — espera de autenticação HTTP

Permitir até 120 segundos para login e alteração de credenciais em hardware lento, com mensagem de processamento e botão desativado. Manter 15 segundos nas restantes operações e preservar o PBKDF2 existente. Caso o pedido expire, informar que as novas credenciais podem já estar guardadas.

## Versão 1.3.3 — instalação root por sessão

Enviar o APK privado e verificado com pm install-create -r, install-write pela entrada padrão e install-commit. Confirmar a resposta final, abandonar sessões falhadas e nunca expor o ficheiro a outros utilizadores. Preservar dados, assinatura, limites e credenciais.


## Horário do ecrã (1.4.0)
Incluir horário diário local/remoto, início/fim HH:mm distintos, intervalos pela meia-noite, desativado por omissão. Usar root para dormir/despertar realmente o ecrã e AlarmManager RTC_WAKEUP exato, sem wakelock noturno. Reprogramar após arranque, atualização, mudança de hora/fuso. Pausar filmes/som no descanso e conservar acesso remoto. Indicar falha root e documentar limitações do fabricante.


## Navegação por toque (1.4.1)
Na apresentação, tocar na metade direita do ecrã avança e na esquerda recua na sequência atual. Aplicar a fotografias e filmes, incluindo margens e ambas as orientações. Recuar no primeiro mostra o último, sem baralhar novamente ao recuar. Reiniciar temporizadores e reprodução do filme selecionado. Preservar toque prolongado para o menu e interações dos diálogos.
