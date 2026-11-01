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
