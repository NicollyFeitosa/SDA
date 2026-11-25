package fs.meta;

import fs.core.FsException;
import fs.disk.BlockDevice;

import java.io.IOException;

public class Bitmap {
    private final BlockDevice dev;
    private final int bloco;
    private final int totalBits;

    public Bitmap(BlockDevice dev, int bloco, int totalBits) {
        this.dev = dev;
        this.bloco = bloco;
        this.totalBits = totalBits;
    }

    public int alocar() throws IOException {
        byte[] bits = dev.readBlock(bloco);
        for (int i = 0; i < totalBits; i++) {
            if (!ligado(bits, i)) {
                alterar(bits, i, true);
                dev.writeBlock(bloco, bits);
                return i;
            }
        }
        throw new FsException("disco cheio");
    }

    public void marcar(int i) throws IOException {
        definir(i, true);
    }

    public void liberar(int i) throws IOException {
        definir(i, false);
    }

    public boolean ocupado(int i) throws IOException {
        verificar(i);
        return ligado(dev.readBlock(bloco), i);
    }

    public int livres() throws IOException {
        byte[] bits = dev.readBlock(bloco);
        int livres = 0;
        for (int i = 0; i < totalBits; i++) {
            if (!ligado(bits, i)) {
                livres++;
            }
        }
        return livres;
    }

    private void definir(int i, boolean valor) throws IOException {
        verificar(i);
        byte[] bits = dev.readBlock(bloco);
        alterar(bits, i, valor);
        dev.writeBlock(bloco, bits);
    }

    private void verificar(int i) {
        if (i < 0 || i >= totalBits) {
            throw new IllegalArgumentException("bit fora do bitmap: " + i);
        }
    }

    private static boolean ligado(byte[] bits, int i) {
        return (bits[i / 8] & (1 << (i % 8))) != 0;
    }

    private static void alterar(byte[] bits, int i, boolean valor) {
        if (valor) {
            bits[i / 8] |= (byte) (1 << (i % 8));
        } else {
            bits[i / 8] &= (byte) ~(1 << (i % 8));
        }
    }
}
