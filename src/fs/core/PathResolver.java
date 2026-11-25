package fs.core;

import fs.disk.BlockDevice;
import fs.disk.Layout;
import fs.meta.Bitmap;
import fs.meta.InodeTable;

import java.io.IOException;

public class PathResolver {
    private final BlockDevice dev;
    private final InodeTable inodes;
    private final Bitmap blocos;

    public PathResolver(BlockDevice dev, InodeTable inodes, Bitmap blocos) {
        this.dev = dev;
        this.inodes = inodes;
        this.blocos = blocos;
    }

    public int resolver(String caminho) throws IOException {
        validar(caminho);
        int atual = Layout.ROOT_INODE;
        for (String parte : caminho.split("/")) {
            if (parte.isEmpty()) {
                continue;
            }
            if (!inodes.ler(atual).isDiretorio()) {
                throw new FsException("nao e um diretorio");
            }
            DirEntry entrada = new Directory(dev, inodes, blocos, atual).buscar(parte);
            if (entrada == null) {
                throw new FsException("caminho nao encontrado");
            }
            atual = entrada.inode();
        }
        return atual;
    }

    public String[] separarPaiENome(String caminho) {
        validar(caminho);
        String limpo = caminho.replaceAll("/+", "/");
        while (limpo.length() > 1 && limpo.endsWith("/")) {
            limpo = limpo.substring(0, limpo.length() - 1);
        }
        int barra = limpo.lastIndexOf('/');
        String pai = barra == 0 ? "/" : limpo.substring(0, barra);
        return new String[] {pai, limpo.substring(barra + 1)};
    }

    private static void validar(String caminho) {
        if (caminho == null || !caminho.startsWith("/")) {
            throw new FsException("caminho deve comecar com /");
        }
    }
}
