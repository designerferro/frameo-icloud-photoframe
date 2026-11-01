# Compilação manual sem os scripts de preparação/build

Este procedimento usa os mesmos ficheiros do projeto. Requer JDK 17 no PATH, Python 3 e os arquivos Android organizados como descrito no README. Todos os diretórios de saída/chaves ficam fora do código.

Versão desta fonte: `1.1.1`, versionCode `3`. Para uma versão futura, atualizar primeiro `VERSION.json` e `AndroidManifest.xml` e adaptar o nome final. As bibliotecas Android 23 contêm as APIs de compilação; os executáveis Android devem ser os próprios do sistema do computador.

## Variáveis e pastas

### GNU/Linux e macOS — shell POSIX

Dentro da raiz do código, adaptar os caminhos:

```sh
PhotoSdk="/caminho/absoluto/android-sdk"
PhotoBt="$PhotoSdk/build-tools/34.0.0"
PhotoJar="$PhotoSdk/platforms/android-23/android.jar"
PhotoOut="../photoframe-manual-build"
PhotoBoot="$PhotoJar:$PhotoBt/core-lambda-stubs.jar"
mkdir -p "$PhotoOut/classes"
```

### Windows — PowerShell

```powershell
$PhotoSdk = "C:\Android\sdk"
$PhotoBt = "$PhotoSdk\build-tools\34.0.0"
$PhotoJar = "$PhotoSdk\platforms\android-23\android.jar"
$PhotoOut = "..\photoframe-manual-build"
$PhotoBoot = "$PhotoJar;$PhotoBt\core-lambda-stubs.jar"
New-Item -ItemType Directory -Force "$PhotoOut\classes"
```

O separador do bootclasspath é `:` em GNU/Linux/macOS e `;` em Windows. Os comandos abaixo assumem um diretório de saída novo para não misturar classes de outra revisão.

## 1. Compilar Java e converter para DEX

GNU/Linux/macOS:

```sh
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PhotoBoot" -d "$PhotoOut/classes" src/pt/photoframe/*.java
jar cf "$PhotoOut/classes.jar" -C "$PhotoOut/classes" .
java -cp "$PhotoBt/lib/d8.jar" com.android.tools.r8.D8 --lib "$PhotoJar" --min-api 23 --output "$PhotoOut" "$PhotoOut/classes.jar"
```

Windows PowerShell:

```powershell
$PhotoSources = (Get-ChildItem "src\pt\photoframe\*.java").FullName
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PhotoBoot" -d "$PhotoOut\classes" $PhotoSources
jar cf "$PhotoOut\classes.jar" -C "$PhotoOut\classes" .
java -cp "$PhotoBt\lib\d8.jar" com.android.tools.r8.D8 --lib "$PhotoJar" --min-api 23 --output "$PhotoOut" "$PhotoOut\classes.jar"
```

## 2. Empacotar o manifesto e juntar classes.dex

GNU/Linux/macOS:

```sh
"$PhotoBt/aapt" package -f -M AndroidManifest.xml -I "$PhotoJar" -F "$PhotoOut/unsigned.apk"
python3 -c "import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],'a'); z.write(sys.argv[2],'classes.dex'); z.close()" "$PhotoOut/unsigned.apk" "$PhotoOut/classes.dex"
"$PhotoBt/zipalign" -f 4 "$PhotoOut/unsigned.apk" "$PhotoOut/aligned.apk"
```

Windows PowerShell:

```powershell
& "$PhotoBt\aapt.exe" package -f -M AndroidManifest.xml -I "$PhotoJar" -F "$PhotoOut\unsigned.apk"
py -3 -c "import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],'a'); z.write(sys.argv[2],'classes.dex'); z.close()" "$PhotoOut\unsigned.apk" "$PhotoOut\classes.dex"
& "$PhotoBt\zipalign.exe" -f 4 "$PhotoOut\unsigned.apk" "$PhotoOut\aligned.apk"
```

Não há `assets/album.json` nem fotografias para empacotar: o álbum é configurado na própria aplicação.

## 3. Assinar e verificar

Se ainda não existir chave, criar uma. O comando pede a palavra-passe: não publicar nem perder esse ficheiro. Para uma atualização, usar a chave que assinou a aplicação instalada.

GNU/Linux/macOS:

```sh
keytool -genkeypair -keystore "$PhotoOut/photoframe-signing.p12" -alias photoframe -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Codex Photoframe local build"
java -jar "$PhotoBt/lib/apksigner.jar" sign --ks "$PhotoOut/photoframe-signing.p12" --ks-key-alias photoframe --out "$PhotoOut/Photoframe-v1.1.1.apk" "$PhotoOut/aligned.apk"
java -jar "$PhotoBt/lib/apksigner.jar" verify --verbose --print-certs "$PhotoOut/Photoframe-v1.1.1.apk"
```

Windows PowerShell:

```powershell
keytool -genkeypair -keystore "$PhotoOut\photoframe-signing.p12" -alias photoframe -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Codex Photoframe local build"
java -jar "$PhotoBt\lib\apksigner.jar" sign --ks "$PhotoOut\photoframe-signing.p12" --ks-key-alias photoframe --out "$PhotoOut\Photoframe-v1.1.1.apk" "$PhotoOut\aligned.apk"
java -jar "$PhotoBt\lib\apksigner.jar" verify --verbose --print-certs "$PhotoOut\Photoframe-v1.1.1.apk"
```

Se a chave já existe, saltar **apenas** o comando `keytool -genkeypair`. Não gerar outra chave para uma atualização.

## 4. Gerar os ficheiros públicos de atualização

Criar no computador um ficheiro temporário `make_release_metadata.py` fora do código, com:

```python
import hashlib, json, sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
out = Path(sys.argv[2]).resolve()
version = json.loads((root / 'VERSION.json').read_text(encoding='utf-8'))
name = 'Photoframe-v' + version['versionName'] + '.apk'
sha = hashlib.sha256((out / name).read_bytes()).hexdigest()
metadata = dict(version, apk=name, sha256=sha, minSdk=23)
(out / 'update.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
(out / 'SHA256SUMS').write_text(sha + '  ' + name + '\n', encoding='utf-8')
```

Executar com Python 3, passando a raiz do código e a pasta de saída. Exemplo POSIX: `python3 ../make_release_metadata.py . "$PhotoOut"`. Exemplo PowerShell: `py -3 ..\make_release_metadata.py . "$PhotoOut"`.

Publicar apenas o APK assinado, `update.json` e `SHA256SUMS` na release estável correspondente à fonte/tag. O APK tem de ter a assinatura usada nas releases anteriores. A chave e os ficheiros intermediários não são assets públicos.

## Referências dos fornecedores

- [Android SDK tools](https://developer.android.com/tools)
- [apksigner](https://developer.android.com/tools/apksigner)
- [zipalign](https://developer.android.com/tools/zipalign)
- [Temurin JDK 17](https://adoptium.net/temurin/releases/?version=17)
- [Python 3](https://www.python.org/downloads/)
