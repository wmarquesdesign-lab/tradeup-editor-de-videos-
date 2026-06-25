# TradeUp Videos Pro — FINAL CORRIGIDO

Projeto Android pronto para gerar APK pelo GitHub Actions.

## Correções desta versão

- Removida a dependência `com.arthenica:ffmpeg-kit-full:6.0-2`, que estava quebrando o build no GitHub Actions.
- Removidos imports e chamadas Java do FFmpegKit que causavam erro de compilação.
- Removida dependência de `androidx.annotation.Nullable`, que não existia no projeto.
- Mantido APK simples e estável em WebView.
- Exportação agora salva o vídeo selecionado na galeria em `Movies/TradeUpVideosPro`.
- Workflow do GitHub Actions mantido em `.github/workflows/build-apk.yml`.

## Estrutura correta na raiz do repositório

A raiz do GitHub deve ficar assim:

- `.github/workflows/build-apk.yml`
- `app/`
- `gradle/`
- `build.gradle`
- `settings.gradle`
- `README.md`

Depois do push, vá em **Actions** e aguarde o workflow **Build APK TradeUp** terminar.
O APK ficará em **Artifacts** com o nome `TradeUpVideosPro-APK`.
