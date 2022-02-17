package com.geekbrains.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private final Server server;
    private final Socket socket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;

    private String nickName;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.inputStream = new DataInputStream(socket.getInputStream());
            this.outputStream = new DataOutputStream(socket.getOutputStream());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        authentication();
                        readMessages();
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    } finally {
                        closeConnection();
                    }
                }
            }).start();
        } catch (IOException exception) {
            throw new RuntimeException("Проблемы при создании обработчика");
        }
    }

    public String getNickName() {
        return nickName;
    }

    public void authentication() throws IOException {
        while (true) {
            String message = inputStream.readUTF();
            if (message.startsWith(ServerCommandConstants.AUTHORIZATION)) {
                String[] authInfo = message.split(" ");
                String nickName = server.getAuthService().getNickNameByLoginAndPassword(authInfo[1], authInfo[2]);
                if (nickName != null) {
                    if (!server.isNickNameBusy(nickName)) {
                        sendMessage("/authok " + nickName);
                        this.nickName = nickName;
                        server.broadcastMessage(nickName + " зашел в чат");
                        server.addConnectedUser(this);
                        return;
                    } else {
                        sendMessage("Учетная запись уже используется");
                    }
                } else {
                    sendMessage("Неверные логин или пароль");
                }
            }
        }
    }

    private void readMessages() throws IOException {
        while (true) {
            String messageInChat = inputStream.readUTF();
            System.out.println("от " + nickName + ": " + messageInChat);

            if (messageInChat.equals(ServerCommandConstants.SHUTDOWN)) {
                return;
            }

            if (messageInChat.startsWith(ServerCommandConstants.PRIVATE_MESSAGE)) {
                String[] messageInfo = messageInChat.split(" ");

                if (messageInfo.length < 3) {
                    server.privateMessage(nickName, "Формат частного сообщения - /w <login> <сообщение>");
                } else {
                    StringBuilder msg = new StringBuilder("приватное от ");
                    msg.append(nickName);
                    msg.append(": ");
                    for (int i = 2; i < messageInfo.length; i++) {
                        msg.append(messageInfo[i]);
                        msg.append(" ");
                    }
                    if (server.privateMessage(messageInfo[1], msg.toString())) {
                        server.privateMessage(nickName, "sent");
                    } else {
                        server.privateMessage(nickName, "Пользователь " + messageInfo[1] + " не зашел в систему");
                    }

                }

            } else {
                server.broadcastMessage(nickName + ": " + messageInChat);
            }
        }
    }

    public void sendMessage(String message) {
        try {
            outputStream.writeUTF(message);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private void closeConnection() {
        server.disconnectUser(this);
        server.broadcastMessage(nickName + " вышел из чата");
        try {
            outputStream.close();
            inputStream.close();
            socket.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}
