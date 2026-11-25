package fs.meta;

import fs.core.FsException;
import fs.disk.BlockDevice;
import fs.disk.Layout;

import java.io.IOException;

public class InodeTable {
    private final BlockDevice dev;
    private final Bitmap bitmap;

    public InodeTable(BlockDevice dev) {
        this.dev = dev;
        this.bitmap = new Bitmap(dev, Layout.INODE_BITMAP, Layout.TOTAL_INODES);
    }

    public Inode ler(int n) throws IOException {
        verificar(n);
        return Inode.fromBytes(dev.readBlock(bloco(n)), deslocamento(n));
    }

    public void gravar(int n, Inode inode) throws IOException {
        verificar(n);
        byte[] dados = dev.readBlock(bloco(n));
        System.arraycopy(inode.toBytes(), 0, dados, deslocamento(n), Layout.INODE_SIZE);
        dev.writeBlock(bloco(n), dados);
    }

    public int alocar(byte tipo) throws IOException {
        if (bitmap.livres() == 0) {
            throw new FsException("sem inodes livres");
        }
        int n = bitmap.alocar();
        gravar(n, new Inode(tipo));
        return n;
    }

    public void liberar(int n) throws IOException {
        gravar(n, new Inode(Inode.LIVRE));
        bitmap.liberar(n);
    }

    public int livres() throws IOException {
        return bitmap.livres();
    }

    private static int bloco(int n) {
        return Layout.INODE_TABLE_START + n / Layout.INODES_PER_BLOCK;
    }

    private static int deslocamento(int n) {
        return (n % Layout.INODES_PER_BLOCK) * Layout.INODE_SIZE;
    }

    private static void verificar(int n) {
        if (n < 0 || n >= Layout.TOTAL_INODES) {
            throw new IllegalArgumentException("inode invalido: " + n);
        }
    }
}
