package fs.journal;

import fs.disk.Layout;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class JournalHeader {
    public static final int MAGIC = 0x4A524E4C;
    public static final int VAZIO = 0;
    public static final int COMMITTED = 1;
    public static final int DESCRICAO_SIZE = 64;

    public int magic = MAGIC;
    public int estado = VAZIO;
    public long txId;
    public int quantidade;
    public String descricao = "";
    public final int[] destinos = new int[Layout.JOURNAL_CAPACITY];

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(Layout.BLOCK_SIZE);
        buf.putInt(magic).putInt(estado).putLong(txId).putInt(quantidade);
        buf.put(Arrays.copyOf(cortar(descricao), DESCRICAO_SIZE));
        for (int destino : destinos) {
            buf.putInt(destino);
        }
        return buf.array();
    }

    public static JournalHeader fromBytes(byte[] dados) {
        ByteBuffer buf = ByteBuffer.wrap(dados);
        JournalHeader h = new JournalHeader();
        h.magic = buf.getInt();
        h.estado = buf.getInt();
        h.txId = buf.getLong();
        h.quantidade = buf.getInt();
        byte[] texto = new byte[DESCRICAO_SIZE];
        buf.get(texto);
        int fim = 0;
        while (fim < DESCRICAO_SIZE && texto[fim] != 0) {
            fim++;
        }
        h.descricao = new String(texto, 0, fim, StandardCharsets.UTF_8);
        for (int i = 0; i < Layout.JOURNAL_CAPACITY; i++) {
            h.destinos[i] = buf.getInt();
        }
        return h;
    }

    public boolean isValid() {
        return magic == MAGIC && quantidade >= 0 && quantidade <= Layout.JOURNAL_CAPACITY;
    }

    private static byte[] cortar(String texto) {
        String atual = texto;
        byte[] bytes = atual.getBytes(StandardCharsets.UTF_8);
        while (bytes.length > DESCRICAO_SIZE) {
            atual = atual.substring(0, atual.length() - 1);
            bytes = atual.getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }
}
