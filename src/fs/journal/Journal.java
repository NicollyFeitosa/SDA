package fs.journal;

import fs.core.FsException;
import fs.disk.Layout;
import fs.disk.VirtualDisk;
import fs.meta.Superblock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Journal {
    private final VirtualDisk disco;
    private final List<String> historico = new ArrayList<>();
    private boolean crashArmado;

    public Journal(VirtualDisk disco) {
        this.disco = disco;
    }

    public Transaction begin(String descricao) throws IOException {
        Superblock sb = Superblock.fromBytes(disco.readBlock(Layout.SUPERBLOCK));
        return new Transaction(sb.lastTxId + 1, descricao, disco);
    }

    public void commit(Transaction tx) throws IOException {
        if (tx.alterados().isEmpty()) {
            return;
        }
        Superblock sb = Superblock.fromBytes(tx.readBlock(Layout.SUPERBLOCK));
        sb.lastTxId = tx.id();
        tx.writeBlock(Layout.SUPERBLOCK, sb.toBytes());

        Map<Integer, byte[]> alterados = tx.alterados();
        if (alterados.size() > Layout.JOURNAL_CAPACITY) {
            throw new FsException("operacao grande demais para o journal");
        }

        JournalHeader header = new JournalHeader();
        header.estado = JournalHeader.COMMITTED;
        header.txId = tx.id();
        header.descricao = tx.descricao();
        header.quantidade = alterados.size();
        int i = 0;
        for (Map.Entry<Integer, byte[]> bloco : alterados.entrySet()) {
            disco.writeBlock(Layout.JOURNAL_DATA_START + i, bloco.getValue());
            header.destinos[i] = bloco.getKey();
            i++;
        }
        disco.sync();

        disco.writeBlock(Layout.JOURNAL_HEADER, header.toBytes());
        disco.sync();

        if (crashArmado) {
            System.out.println("crash: programa interrompido apos o commit da transacao #" + tx.id());
            System.out.flush();
            Runtime.getRuntime().halt(1);
        }

        for (Map.Entry<Integer, byte[]> bloco : alterados.entrySet()) {
            disco.writeBlock(bloco.getKey(), bloco.getValue());
        }
        disco.sync();

        limpar();
        historico.add("#" + tx.id() + " " + tx.descricao() + " OK");
    }

    public void recover() throws IOException {
        JournalHeader header = JournalHeader.fromBytes(disco.readBlock(Layout.JOURNAL_HEADER));
        if (!header.isValid()) {
            limpar();
            return;
        }
        if (header.estado != JournalHeader.COMMITTED) {
            return;
        }
        for (int i = 0; i < header.quantidade; i++) {
            disco.writeBlock(header.destinos[i], disco.readBlock(Layout.JOURNAL_DATA_START + i));
        }
        disco.sync();
        limpar();
        historico.add("#" + header.txId + " " + header.descricao + " RECUPERADA");
        System.out.println("journal: transacao #" + header.txId + " (" + header.descricao + ") recuperada");
    }

    public void armarCrash() {
        crashArmado = true;
    }

    public List<String> historico() {
        return Collections.unmodifiableList(historico);
    }

    public static void inicializar(VirtualDisk disco) throws IOException {
        disco.writeBlock(Layout.JOURNAL_HEADER, new JournalHeader().toBytes());
    }

    private void limpar() throws IOException {
        inicializar(disco);
        disco.sync();
    }
}
