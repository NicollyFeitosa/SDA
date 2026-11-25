package fs.core;

import fs.disk.BlockDevice;
import fs.disk.Layout;
import fs.meta.Bitmap;
import fs.meta.Inode;
import fs.meta.InodeTable;

import java.io.IOException;
import java.util.Arrays;

public class File {
    public static final int TAMANHO_MAXIMO = Inode.MAX_BLOCOS * Layout.BLOCK_SIZE;

    private final BlockDevice dev;
    private final InodeTable inodes;
    private final Bitmap blocos;
    private final int numero;

    public File(BlockDevice dev, InodeTable inodes, Bitmap blocos, int inodeNumero) {
        this.dev = dev;
        this.inodes = inodes;
        this.blocos = blocos;
        this.numero = inodeNumero;
    }

    public static int criar(InodeTable inodes) throws IOException {
        return inodes.alocar(Inode.ARQUIVO);
    }

    public int numero() {
        return numero;
    }

    public byte[] ler() throws IOException {
        Inode inode = inodes.ler(numero);
        byte[] conteudo = new byte[inode.tamanho];
        for (int i = 0, pos = 0; pos < inode.tamanho; i++, pos += Layout.BLOCK_SIZE) {
            byte[] dados = dev.readBlock(inode.blocos[i]);
            System.arraycopy(dados, 0, conteudo, pos, Math.min(Layout.BLOCK_SIZE, inode.tamanho - pos));
        }
        return conteudo;
    }

    public void escrever(byte[] conteudo) throws IOException {
        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new FsException("arquivo excede 5 KB");
        }
        liberarBlocos();
        Inode inode = inodes.ler(numero);
        for (int i = 0, pos = 0; pos < conteudo.length; i++, pos += Layout.BLOCK_SIZE) {
            int bloco = blocos.alocar();
            int fim = Math.min(pos + Layout.BLOCK_SIZE, conteudo.length);
            dev.writeBlock(bloco, Arrays.copyOf(Arrays.copyOfRange(conteudo, pos, fim), Layout.BLOCK_SIZE));
            inode.blocos[i] = bloco;
        }
        inode.tamanho = conteudo.length;
        inode.tocar();
        inodes.gravar(numero, inode);
    }

    public void liberarBlocos() throws IOException {
        Inode inode = inodes.ler(numero);
        for (int i = 0; i < Inode.MAX_BLOCOS; i++) {
            if (inode.blocos[i] >= 0) {
                blocos.liberar(inode.blocos[i]);
                inode.blocos[i] = -1;
            }
        }
        inode.tamanho = 0;
        inode.tocar();
        inodes.gravar(numero, inode);
    }
}
