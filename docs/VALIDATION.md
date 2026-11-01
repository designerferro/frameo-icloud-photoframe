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


## Vídeo na versão 1.2.0

O ensaio Android usa filmes sintéticos com som AAC: H.264 de 640×360, H.264 de 1920×1080 e uma variante com metadados de rotação de 90 graus. Verifica corte para dois segundos, preservação do áudio, redução de resolução, orientação vertical, reprodução no leitor da aplicação, avanço ao limite de um segundo, volumes 0/10 e libertação do leitor ao pausar.

Requisitos adicionais: ffmpeg e adb no PATH, uma única moldura de teste autorizada e a chave de assinatura correspondente ao APK. **O teste reproduz brevemente som no volume máximo**, instala o APK indicado e repõe as definições anteriores. Não executar numa moldura em utilização sem preparar o ambiente.

```text
PY tools/device_video_test.py --sdk CAMINHO_SDK --jdk CAMINHO_JDK --keystore CAMINHO_CHAVE --apk CAMINHO_APK
```

O teste temporário é compilado e assinado localmente; não integra o APK de distribuição. A integração com o iCloud continua a depender dos endpoints do álbum público e dos formatos fornecidos pelo serviço. Os testes sintéticos não demonstram suporte a todos os vídeos iCloud.

Resultado do ensaio de 1.2.0 em Android 6: aprovado. Saídas sintéticas de 640×360, 1280×720 e 400×720 (vertical), com áudio AAC e corte de duração; reprodução, avanço automático, volumes 0/10 e libertação ao pausar aprovados.


## Privacidade da configuração na versão 1.2.1

Com um álbum de teste configurado, executar o mesmo auxiliar com `--privacy-only` para verificar o diálogo real: campo inicialmente vazio, entrada mascarada, ausência do token guardado nos textos, ligação inválida recusada e gravação vazia sem alterar álbum nem fotografias. Este ensaio não reproduz som e não imprime a ligação guardada.

O `.gitignore` pode existir localmente, deve incluir a entrada `.gitignore` e não pode estar no índice Git. As verificações correm tanto no ambiente local com este ficheiro ignorado como no GitHub sem o ficheiro.


## Acesso remoto e ordem na versão 1.3.0

`tools/check.py` executa também `RemoteCoreTest`: password/salt PBKDF2, tokens, invalidação das credenciais iniciais e limites do parser HTTP (corpos grandes, cabeçalhos duplicados, codificação ambígua, parâmetros em URL e proteções do navegador).

Com uma moldura de teste com álbum configurado, executar o auxiliar `tools/device_video_test.py` com os mesmos argumentos de compilação e a opção `--remote-only`. O ensaio Android verifica: login anónimo/errado, obrigação de trocar credenciais, invalidação de sessões, recusa de CSRF/Origin, ausência do URL/token guardado nas respostas, gravação de configurações, ordem aleatória sem repetição na volta, transição entre voltas, datas ascendentes, cache antiga, limites de volume, confirmação de remoção, logout e disponibilidade com as definições Android abertas. Usa credenciais temporárias e repõe as anteriores no fim, sem apagar o álbum.

Verificar também a página real num navegador na LAN, a persistência após reinício e a desativação/reativação local. As restrições de arranque de versões Android mais recentes e do fabricante continuam a aplicar-se; o alvo principal é Android 6.

Resultado em Android 6: ensaio remoto aprovado, incluindo autenticação, privacidade, configurações e ordem. Após reinício, serviço em primeiro plano e página/recursos acessíveis pela LAN; a consulta sem sessão das configurações foi recusada. O DHCP pode atribuir um endereço diferente após reiniciar, pelo que o menu deve ser consultado novamente.


## Sincronização com filmes na versão 1.3.1

O ensaio de vídeo inclui agora dois filmes sintéticos de 12 segundos, com o áudio como primeira faixa: um H.264 com B-frames e outro Baseline sem B-frames. A duração pedida é de 30 segundos, de modo a processar o filme completo, além dos casos anteriores com corte de 2 segundos e conversão horizontal/vertical. Ambos devem terminar com áudio e duração entre 10 e 13 segundos, sem bloquear o gravador MP4.

A documentação Android só garante gravação de B-frames em MP4 a partir de API 25. Em versões anteriores, a aplicação examina os timestamps antes da cópia direta e recodifica quando necessário. Referência: [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer).

Resultado do ensaio em Android 6: aprovado. Filmes sintéticos de 12 segundos com e sem B-frames terminam com áudio, além dos casos de corte, redimensionamento e rotação. Reprodução/avanço/volume aprovados. Sincronização real concluída após a correção sem bloquear na preparação do filme. Dados e credenciais preservados.


## Espera HTTP na versão 1.3.2

Diagnóstico num equipamento Android 6: o cálculo PBKDF2 demorou cerca de 18 segundos, ultrapassando os 15 segundos da página anterior. O teste usa dados fictícios e não altera as credenciais.

`node --test tests/remote-api.test.js` verifica login e alteração de credenciais com resposta aos 18,1 segundos, o limite de 120 segundos, a mensagem de recuperação e a manutenção dos 15 segundos nas restantes operações. Estes testes usam um relógio simulado; não constituem um ensaio completo num navegador ligado à moldura. As verificações Java/HTTP, Python, privacidade e assinatura Android 6 passaram.
