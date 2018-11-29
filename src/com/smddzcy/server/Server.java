package com.smddzcy.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class Server {
    private static final int SOCKET_PORT = 63012;

    public static void main(String[] args) throws InterruptedException, UnknownHostException {
        ServerSocket serverSocket = null;
        while (serverSocket == null) {
            // Try to initiate a server socket
            try {
                serverSocket = new ServerSocket(SOCKET_PORT);
            } catch (IOException e) {
                // in case of failure, try again in .5s
                System.out.println("Failed to create the server socket.");
                Thread.sleep(500);
            }
        }

        String ip = InetAddress.getLocalHost().toString().split("/")[1];
        System.out.println("Server socket is running at " + ip + ":" + SOCKET_PORT);

        // Accept connections
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("New socket connection from " + socket.getInetAddress());
                new SocketHandler(socket).start();
            } catch (IOException e) {
                System.out.println("Error while handling the socket connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
