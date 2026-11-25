package fs;

import fs.core.FileSystemSimulator;
import fs.core.FsException;
import fs.disk.VirtualDisk;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        String caminho = args.length > 0 ? args[0] : "disco.img";
        try {
            if (!VirtualDisk.exists(caminho)) {
                FileSystemSimulator.formatar(caminho);
                System.out.println("disco formatado: " + caminho);
            }
            try (FileSystemSimulator fs = FileSystemSimulator.montar(caminho)) {
                System.out.println("disco montado: " + caminho);
            }
        } catch (FsException e) {
            System.out.println("erro: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("erro de E/S: " + e.getMessage());
        }
    }
}
