package com.kronosad.konverse.server;

import com.kronosad.konverse.common.KonverseAPI;
import com.kronosad.konverse.common.auth.Authentication;
import com.kronosad.konverse.common.objects.ChatMessage;
import com.kronosad.konverse.common.objects.PrivateMessage;
import com.kronosad.konverse.common.objects.Version;
import com.kronosad.konverse.common.packets.Packet;
import com.kronosad.konverse.common.packets.Packet02ChatMessage;
import com.kronosad.konverse.common.packets.Packet03UserListChange;
import com.kronosad.konverse.common.user.AuthenticatedUser;
import com.kronosad.konverse.common.user.User;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * User: russjr08
 * Date: 1/17/14
 * Time: 5:30 PM
 */
public class Server {

    private ServerSocket server;
    private Version version = KonverseAPI.API_VERSION;
    private Authentication authenticator;

    protected AuthenticatedUser serverUser;
    protected ArrayList<NetworkUser> users = new ArrayList<NetworkUser>();

    protected boolean running = false;

    protected static Server instance;


    public Server(String args[]) {
        serverUser = new AuthenticatedUser();

        for(String string : args) {
            if(string.contains("--auth-server=")){
                authenticator = new Authentication(string.split("=")[0]);
            }
        }

        if(authenticator == null) {
            authenticator = new Authentication();
        }


        try {
            Field username = serverUser.getClass().getSuperclass().getDeclaredField("username");

            username.setAccessible(true);

            username.set(serverUser, "Server");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }


        System.out.println("Opening Server on port: " + args[0]);

        try {
            server = new ServerSocket(Integer.valueOf(args[0]));
            System.out.println("Sucessfully bounded to port!");
            running = true;
            serve();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("There was a problem binding to port: " + args[0]);
        }

        instance = this;


        while (running) {
            Scanner in = new Scanner(System.in);
            String response = in.nextLine();

            if (response.equalsIgnoreCase("stop")) {
                try {
                    System.out.println("Stopping Server...");
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.setMessage("[WARNING: Server is shutting down, disconnecting all clients!]");
                    chatMessage.setUser(this.serverUser);
                    Packet02ChatMessage chatPacket = new Packet02ChatMessage(Packet.Initiator.SERVER, chatMessage);
                    sendPacketToClients(chatPacket);

                    for (NetworkUser user : users) {
                        user.getSocket().close();
                    }

                    this.server.close();
                    running = false;
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (response.startsWith("kick")) {
                String username = response.split(" ")[1];
                System.out.println("Kicking user: " + username);
                StringBuilder kickBuilder = new StringBuilder();
                for (int i = 0; i < response.split(" ").length; i++) {
                    if (i != 0 && i != 1) {
                        kickBuilder.append(response.split(" ")[i] + " ");
                    }
                }

                User user = new User();
                try {
                    Field usernameField = user.getClass().getDeclaredField("username");
                    usernameField.setAccessible(true);
                    usernameField.set(user, username);
                    NetworkUser networkUser = getNetworkUserFromUser(user);
                    if (networkUser == null) {
                        System.out.println("User was not found!");

                    } else {
                        networkUser.disconnect(kickBuilder.toString(), true);

                    }

                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            } else {
                ChatMessage message = new ChatMessage();
                message.setMessage(response);
                message.setUser(serverUser);

                Packet02ChatMessage packet02ChatMessage = new Packet02ChatMessage(Packet.Initiator.SERVER, message);
                try {
                    sendPacketToClients(packet02ChatMessage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

    }

    public void sendPacketToClients(Packet packet) throws IOException {
        if (packet instanceof Packet02ChatMessage) {
            Packet02ChatMessage chatPacket = (Packet02ChatMessage) packet;
            if (((Packet02ChatMessage) packet).getChat().getMessage().isEmpty() || ((Packet02ChatMessage) packet).getChat().getMessage().trim().isEmpty()) {
                return; /** Null message, don't send. **/}
            if (chatPacket.isPrivate()) {
                PrivateMessage msg = chatPacket.getPrivateMessage();
                for (NetworkUser user : users) {
                    if (user.getUsername().equals(msg.getRecipient().getUsername()) || user.getUsername().equals(chatPacket.getChat().getUser().getUsername())) {
                        sendPacketToClient(user, packet);
                        System.out.println(String.format("[%s -> %s] %s", chatPacket.getChat().getUser().getUsername(), msg.getRecipient().getUsername(), chatPacket.getChat().getMessage()));
                        return;
                    }
                }

            } else {
                System.out.println("[" + chatPacket.getChat().getUser().getUsername() + "] " + chatPacket.getChat().getMessage());

            }

        }
        for (NetworkUser user : users) {
            PrintWriter writer = new PrintWriter(user.getSocket().getOutputStream(), true);
            writer.println(packet.toJSON());
        }


    }

    public void sendPacketToClient(NetworkUser user, Packet packet) throws IOException {
        if (packet instanceof Packet02ChatMessage) {
            Packet02ChatMessage chatPacket = (Packet02ChatMessage) packet;
            if (((Packet02ChatMessage) packet).getChat().getMessage().isEmpty()) {
                return; /** Null message, don't send. **/}
            System.out.println("[" + chatPacket.getChat().getUser().getUsername() + "] " + chatPacket.getChat().getMessage());
        }

        PrintWriter writer = new PrintWriter(user.getSocket().getOutputStream(), true);
        writer.println(packet.toJSON());


    }

    public NetworkUser getNetworkUserFromUser(User user) {
        for (NetworkUser network : users) {
            if (network.getUsername().equalsIgnoreCase(user.getUsername())) {
                return network;
            }
        }

        return null;
    }

    public List<User> getOnlineUsers(){
        List<User> online = new ArrayList<User>();
        for(NetworkUser onlineUser : users){
            online.add(onlineUser);
        }
        return online;
    }

    public void serve() {
        new Thread() {
            public void run() {
                while (running) {
                    try {
                        new ConnectionHandler(Server.this, server.accept());
                    } catch (IOException e) {
                        System.err.println("Error accepting connection!");
                        e.printStackTrace();
                    }
                }
            }
        }.start();

    }

    public void broadcastUserChange(User user, boolean newUser) throws IOException {
        sendUserChange();

        ChatMessage message = new ChatMessage();
        String msg;
        if(newUser){
            msg = "joined!";
        }else{
            msg = "left!";
        }
        message.setMessage(user.getUsername() + " has " + msg);
        message.setUser(serverUser);

        Packet02ChatMessage chatPacket = new Packet02ChatMessage(Packet.Initiator.SERVER, message);

        sendPacketToClients(chatPacket);

        Packet03UserListChange changePacket = new Packet03UserListChange(Packet.Initiator.SERVER, getOnlineUsers());

        sendPacketToClients(changePacket);

    }

    public void sendUserChange() throws IOException {
        Packet03UserListChange changePacket = new Packet03UserListChange(Packet.Initiator.SERVER, getOnlineUsers());
        sendPacketToClients(changePacket);
    }


    public Version getVersion() {
        return version;
    }

    public Authentication getAuthenticator() { return authenticator; }

    public static void main(String[] args) {
        new Server(args);
    }

    public static Server getInstance() {
        return instance;
    }

}
