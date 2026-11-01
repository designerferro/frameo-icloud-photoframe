# Histórico

## 1.3.1 — versionCode 7

- Gravação de áudio e vídeo intercalados, evitando bloqueios do gravador MP4 no Android 6.
- Deteção de B-frames antes de gravar diretamente em Android anterior a API 25; conversão para H.264 sem B-frames compatível.
- Estado de sincronização atualizado ao consultar, descarregar e preparar filmes, sem manter o erro anterior durante uma nova tentativa.
- Mensagem de falha DNS mais clara, mantendo a repetição após um minuto e a cópia local.
- Regressão com filmes sintéticos mais longos, áudio como primeira faixa e H.264 com/sem B-frames.

## 1.3.0 — versionCode 6

- Administração HTTP na LAN, com página incluída no APK e serviço Android em primeiro plano.
- Login inicial admin / admin; alteração obrigatória do utilizador e palavra-passe antes de configurar.
- Sessões, hash PBKDF2, limites de tentativas/pedidos e proteção de origem/CSRF.
- Configurações e ações do menu disponíveis no navegador; URL guardado do álbum nunca devolvido.
- Ordem Aleatório ou Por data da fotografia, para fotografias e filmes, também offline.
- Datas guardadas no manifesto, migração da cache anterior e preservação da sequência em sincronizações sem alterações.
- Sem integração Home Assistant.

## 1.2.1 — versionCode 5

- A configuração nunca preenche o campo com o URL/token guardado do álbum.
- Nova ligação introduzida em campo vazio e mascarado; guardar vazio mantém o álbum existente.
- `.gitignore` local que ignora a si próprio, sem o publicar; verificações adaptadas.
- Prompt com o utilizador GitHub definido uma só vez e restantes referências parametrizadas.

## 1.2.0 — versionCode 4

- Fotografias e filmes na mesma apresentação offline.
- Preparação MP4/H.264 local, ajustada ao ecrã quando necessário, com áudio AAC.
- Duração máxima de 30 segundos por omissão, configurável até ao teto calculado pelo espaço disponível.
- Volume persistente de 0 a 10; 0 sem som e 10 máximo.
- Downloads de filmes para ficheiro com orçamento de armazenamento.
- Autoria integral do código pelo Codex destacada na apresentação pública.

## 1.1.1 — versionCode 3

- Preparação de instalação root em pasta exclusiva e privada, com limpeza automática.
- Ação para apagar álbum e cache; limpeza selecionada por defeito ao mudar de álbum.
- Removida a importação de álbuns/fotografias embutidos de protótipos antigos.
- Falhas de sincronização registam apenas o tipo de erro, sem URLs de álbum.
- Pesquisa de secrets na fonte/histórico e documentação sem registos pessoais de utilização.

## 1.1.0 — versionCode 2

- APK público sem álbum, fotografias ou credenciais; configuração do álbum na moldura.
- Atualizações por GitHub Releases, comparação por versionCode e reinstalação manual da mesma versão.
- Validação SHA-256, identidade do pacote e assinatura antes de instalar.
- Instalação automática com root; instalador Android com confirmação quando não há root.
- Retoma da apresentação após substituir o pacote.
- Fotografia inteira sobre fundo suave ou preenchimento com recorte; orientação automática, horizontal ou vertical.
- Ferramentas Python para Windows, GNU/Linux e macOS; instruções manuais.
- Autoria técnica Codex e prompt de recriação incluído.

## 1.0 — versionCode 1

Protótipo local: apresentação offline, álbum incluído na compilação e sincronização direta com iCloud. Não distribuído como release pública.
