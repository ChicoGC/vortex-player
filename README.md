# Vortex

Player de música para Android, vibecodado em poucas horas então não leve esse projeto tão a sério.
## Como usar??

1 - **Músicas**
Para músicas, você precisa usar um método de sua preferência e baixar o mp3 da música (e fazer algumas alterações)

2 - **Artistas**
Para aparecerem os nomes corretos dos artistas, você deve manualmente renomear o arquivo para:
[Batata - Musicalegal.mp3]
Substitua "Batata" pelo nome do artista e substitua "Musicalegal" com o nome da música (se você colocar o nome errado, as letras vão ficar erradas/dessincronizadas!!)

3 - **Capas**
O app ainda não tem função de importar as capas oficiais das músicas, mas para evitar um vazio no design, você pode colocar uma pasta com várias fotos que irão ser mostradas no lugar.

## Recursos

- **Sua pasta de músicas**: escolha qualquer pasta do celular (MP3, M4A, FLAC, OGG, OPUS, WAV), incluindo subpastas.
- **Artista e título automáticos**: lidos das tags do arquivo ou do nome (`Artista - Música (Official Video).mp3` → *Artista* / *Música*).
- **Customização**: 8 temas de várias cores para você usar e se divertir.
- **Letras**: arquivos `.lrc` ao lado da música ou busca online no [LRCLIB](https://lrclib.net), com destaque da linha atual.
- **Sincronização de letras**: ajuste de ±0,5 s, segure numa linha para sincronizá-la e busque outra versão da letra.
- **Saída de áudio**: escolha entre alto-falante, fone com fio ou Bluetooth.
- **Notificação e segundo plano**: a música continua tocando fora do app, com capa, nome e botões de voltar, pausar e pular na notificação e na tela de bloqueio.
- **Controles do fone**: obedece aos toques do fone Bluetooth (configurados no app do fone). No fone com fio: 1 toque pausa, 2 pulam e 3 voltam. A música pausa sozinha se o fone desconectar.

## Como compilar

1. Instale o [Android Studio](https://developer.android.com/studio).
2. Clone o repositório e abra a pasta no Android Studio.
3. Conecte um celular com Android 10 ou superior (depuração USB ativada) e clique em **Run ▶**.

## Permissões

- **Pastas**: o app só lê as pastas que você escolhe (Storage Access Framework). Não pede acesso a todos os arquivos.
- **Internet**: usada apenas para buscar letras no LRCLIB.

## Licença

[MIT](LICENSE)
