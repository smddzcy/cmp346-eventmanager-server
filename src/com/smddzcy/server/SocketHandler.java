package com.smddzcy.server;

import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

class Message implements Serializable {
    private static final long serialVersionUID = 8924499989713919097L;
    private String type;
    private String payload;

    public Message() {
    }

    public Message(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Message message = (Message) o;
        return Objects.equals(type, message.type) &&
               Objects.equals(payload, message.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, payload);
    }

    @Override
    public String toString() {
        return "Message{" +
               "type='" + type + '\'' +
               ", payload='" + payload + '\'' +
               '}';
    }
}

class SuccessMessage {
    String type;
    List<Incident> payload;

    public SuccessMessage() {
    }

    public SuccessMessage(String type, List<Incident> payload) {
        this.type = type;
        this.payload = payload;
    }
}

public class SocketHandler extends Thread {
    Socket socket;
    BufferedReader in;
    BufferedWriter out;
    private final Gson gson = new Gson();

    public SocketHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream()));

            // Handle the msg
            String msg = in.readLine();
            Message obj = gson.fromJson(msg, Message.class);
            System.out.println("Message: " + msg + ", Object: " + obj.toString());
            handleMsg(obj);

            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendReply(String msg) {
        try {
            out.write(msg);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendSuccessReply() {
        SuccessMessage msg = new SuccessMessage("success", DB.getIncidents());
        sendReply(gson.toJson(msg));
    }

    private void sendFailReply(String msg) {
        sendReply(gson.toJson(new Message("fail", msg)));
    }

    private void handleMsg(Message msg) {
        String[] parts = msg.getPayload().split(":::");
        try {
            switch (msg.getType()) {
                case "login":
                    Optional<User> user = DB.getUsers().stream()
                                            .filter(u -> u.getUsername()
                                                          .equals(parts[0]))
                                            .findFirst();
                    if (!user.isPresent()) {
                        DB.addUser(new User(parts[0], parts[1]));
                        sendSuccessReply();
                        return;
                    }
                    if (!user.get().getPassword().equals(parts[1])) {
                        sendFailReply("Wrong password");
                        return;
                    }
                    sendSuccessReply();
                    break;
                case "addIncident":
                    DB.addIncident(new Incident(parts[0], parts[1], parts[2]));
                    sendSuccessReply();
                    break;
                case "updateIncident":
                    DB.updateIncident(UUID.fromString(parts[0]), new Incident(parts[1], parts[2], parts[3]));
                    sendSuccessReply();
                    break;
                case "deleteIncident":
                    DB.removeIncident(UUID.fromString(parts[0]));
                    sendSuccessReply();
                    break;
                case "listen":
                    sendSuccessReply();
                    DB.onIncidentsChange(socket, incidents -> sendSuccessReply());
                    // wait as long as the socket is connected, because they're listening to changes
                    while (socket.isConnected()) {}
                default:
                    sendFailReply("Unknown message type: " + msg.getType());
                    break;
            }
        } catch (Exception e) {
            // Unexpected error
            sendFailReply(e.getMessage());
            e.printStackTrace();
        }
    }
}
