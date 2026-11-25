package fs;

import fs.disk.VirtualDisk;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        String caminho = args.length > 0 ? args[0] : "disco.img";
        if (!VirtualDisk.exists(caminho)) {
            VirtualDisk.create(caminho);
            System.out.println("disco criado: " + caminho);
        }
        try (VirtualDisk disco = new VirtualDisk(caminho)) {
            System.out.println("disco aberto: " + caminho + " (" + disco.tamanho() + " bytes)");
        }
    }
}
