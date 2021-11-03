package ru.postlife.java.lesson2_nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class BuffersExamples {

    public static void main(String[] args) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(5);

        byte ch = 'a';
        for (int i = 0; i < 3; i++) {
            buffer.put((byte) (ch + i));
        }

        //     |
        // 1 2 3 4 5 reset

        // buffer.flip(); запись переводим в чтение
        // buffer.rewind(); даем читать другим не меняя буфер
        // buffer.clear(); все кто надо прочел пишем что то новое в буффер

        while (buffer.hasRemaining()) {
            System.out.println((char) buffer.get());
        }

        buffer.clear();
        Path text = Paths.get("cloud-storage-nov-server", "server", "root", "Hello.txt");
        SeekableByteChannel channel = Files.newByteChannel(text);
        System.out.println(channel);
        byte[] result = new byte[(int) channel.size()];
        int pos = 0;
        while (true) {
            int read = channel.read(buffer);
            if (read <= 0) {
                break;
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                result[pos] = buffer.get();
                pos++;
            }
            buffer.clear();
        }
        System.out.println(new String(result, StandardCharsets.UTF_8));
        Path copy = Paths.get("cloud-storage-nov-server", "server", "root", "copy.txt");
        SeekableByteChannel copyChannel = Files.newByteChannel(copy, StandardOpenOption.WRITE);

        buffer.clear();

        // cmd + option + v
        // Scanner scanner = new Scanner(System.in);

        byte[] bytes = "Приветствуем вас в нашей системе!".getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < bytes.length; i++) {
            buffer.put(bytes[i]);
            if (i % 5 == 0) {
                buffer.flip();
                copyChannel.write(buffer);
                buffer.clear();
            }
            if (i == bytes.length - 1 && (i + 1) % 5 != 0) {
                buffer.flip();
                copyChannel.write(buffer);
            }
        }

    }
}
