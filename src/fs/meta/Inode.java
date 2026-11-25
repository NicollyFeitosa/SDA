package fs.meta;

import fs.disk.Layout;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Inode {
    public static final byte LIVRE = 0;
    public static final byte ARQUIVO = 1;
    public static final byte DIRETORIO = 2;
    public static final int MAX_BLOCOS = 10;

    public byte tipo;
    public int tamanho;
    public long criado;
    public long modificado;
    public final int[] blocos = new int[MAX_BLOCOS];

    public Inode(byte tipo) {
        this.tipo = tipo;
        Arrays.fill(blocos, -1);
        if (tipo != LIVRE) {
            criado = System.currentTimeMillis();
            modificado = criado;
        }
    }

    public boolean isDiretorio() {
        return tipo == DIRETORIO;
    }

    public boolean isArquivo() {
        return tipo == ARQUIVO;
    }

    public void tocar() {
        modificado = System.currentTimeMillis();
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(Layout.INODE_SIZE);
        buf.put(tipo).putInt(tamanho).putLong(criado).putLong(modificado);
        for (int b : blocos) {
            buf.putInt(b);
        }
        return buf.array();
    }

    public static Inode fromBytes(byte[] dados, int offset) {
        ByteBuffer buf = ByteBuffer.wrap(dados, offset, Layout.INODE_SIZE);
        Inode inode = new Inode(LIVRE);
        inode.tipo = buf.get();
        inode.tamanho = buf.getInt();
        inode.criado = buf.getLong();
        inode.modificado = buf.getLong();
        for (int i = 0; i < MAX_BLOCOS; i++) {
            inode.blocos[i] = buf.getInt();
        }
        return inode;
    }
}
