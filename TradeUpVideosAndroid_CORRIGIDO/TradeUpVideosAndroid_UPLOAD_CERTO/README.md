# TradeUp Videos Pro — Corrigido

Projeto Android para gerar APK pelo GitHub Actions.

## Como enviar para o GitHub

Envie a pasta completa mantendo esta estrutura:

- `.github/workflows/build-apk.yml`
- `app/`
- `gradle/`
- `build.gradle`
- `settings.gradle`
- `README.md`

Depois vá em **Actions > Build APK TradeUp > Run workflow**.

O APK ficará em **Artifacts** com o nome `TradeUpVideosPro-APK`.

## Correções feitas

- Workflow do GitHub Actions corrigido para instalar Gradle 8.7 e Android SDK.
- Exportação corrigida para salvar o vídeo na galeria em `Movies/TradeUpVideosPro` usando MediaStore.
- Permissões ajustadas para Android novo e antigo.
- Interface HTML ajustada.
- Código Java melhorado para importar arquivos e exibir erros.
