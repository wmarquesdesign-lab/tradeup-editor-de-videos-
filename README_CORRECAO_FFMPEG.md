# Correção FFmpegKit

Correções aplicadas nesta versão:

- Troca da dependência FFmpegKit para `com.arthenica:ffmpeg-kit-full-gpl:6.0-2.LTS`.
- Inclusão do repositório público Appodeal em `settings.gradle` para resolver o artefato no GitHub Actions.
- Ajuste do comando FFmpeg para áudio com `-stream_loop -1`, evitando música curta terminar antes do vídeo.
- Correção da expressão `setpts` usada na velocidade dos clipes.

Depois de subir no GitHub, rode o Actions novamente.
