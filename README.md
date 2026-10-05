# Vortex

Player de música para Android com visual *liquid glass*, temas de cores e letras sincronizadas.

## Recursos

- **Sua pasta de músicas**: escolha qualquer pasta do celular (MP3, M4A, FLAC, OGG, OPUS, WAV), incluindo subpastas.
- **Artista e título automáticos**: lidos das tags do arquivo ou do nome (`Artista - Música (Official Video).mp3` → *Artista* / *Música*).
- **Liquid glass**: 8 temas (escuros e claros), intensidade do vidro ajustável, fundo animado com a cor da capa.
- **Fotos de capa**: use as capas originais ou escolha uma pasta de fotos para aparecerem aleatoriamente a cada música.
- **Letras**: arquivos `.lrc` ao lado da música ou busca online no [LRCLIB](https://lrclib.net), com destaque da linha atual.
- **Sincronização de letras**: ajuste de ±0,5 s, segure numa linha para sincronizá-la e busque outra versão da letra.
- **Saída de áudio**: escolha entre alto-falante, fone com fio ou Bluetooth.

## Como compilar

1. Instale o [Android Studio](https://developer.android.com/studio).
2. Clone o repositório e abra a pasta no Android Studio.
3. Conecte um celular com Android 10 ou superior (depuração USB ativada) e clique em **Run ▶**.

## Permissões

- **Pastas**: o app só lê as pastas que você escolhe (Storage Access Framework). Não pede acesso a todos os arquivos.
- **Internet**: usada apenas para buscar letras no LRCLIB.

## Licença

[MIT](LICENSE)
