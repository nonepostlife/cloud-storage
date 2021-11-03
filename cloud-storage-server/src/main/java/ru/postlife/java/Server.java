package ru.postlife.java;

import java.net.ServerSocket;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server {

    public final static int SOCKET_PORT = 9979;

    public static void main(String[] args) {
        try(ServerSocket server = new ServerSocket(SOCKET_PORT)) {
            log.debug("Server started...");
            while (true) {
                Socket socket = server.accept();
                log.debug("Client accepted...");
                Handler handler = new Handler(socket);
                new Thread(handler).start();
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }
}
