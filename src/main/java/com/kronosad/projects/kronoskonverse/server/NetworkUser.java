package com.kronosad.projects.kronoskonverse.server;

import com.google.gson.Gson;
import com.kronosad.projects.kronoskonverse.common.objects.ChatMessage;
import com.kronosad.projects.kronoskonverse.common.packets.Packet;
import com.kronosad.projects.kronoskonverse.common.packets.Packet02ChatMessage;
import com.kronosad.projects.kronoskonverse.common.packets.Packet03UserListChange;
import com.kronosad.projects.kronoskonverse.common.packets.Packet04Disconnect;
import com.kronosad.projects.kronoskonverse.common.user.User;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * User: russjr08
 * Date: 1/17/14
 * Time: 5:43 PM
 */
public class NetworkUser extends User {

    public transient Socket socket;

    public NetworkUser(Socket socket, String name, String uuid, boolean elevated){
        this.socket = socket;
        this.username = name;
        this.uuid = uuid;
        this.elevated = elevated;
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public String toJSON() {
        return new Gson().toJson(this);
    }

    /**
     * Should only be used on the Server-Side!
     * @param reason Reason to kick.
     * @param isKick Used in the disconnect packet.
     * @throws java.lang.IllegalAccessError - If used on the Client-Side, or the socket is null.
     */
    public void disconnect(String reason, boolean isKick){
        if(socket == null){
            throw new IllegalAccessError("Socket is null! This must be ran on the Server-Side!");
        }

        Packet04Disconnect packet = new Packet04Disconnect(Packet.Initiator.SERVER, this, true);
        packet.setMessage(reason);

        ChatMessage message = new ChatMessage();
        message.setMessage(this.getUsername() + " has left.");
        message.setUser(Server.getInstance().serverUser);

        Packet02ChatMessage chatPacket = new Packet02ChatMessage(Packet.Initiator.SERVER, message);

        Server.getInstance().users.remove(this);

        Packet03UserListChange change = new Packet03UserListChange(Packet.Initiator.SERVER, Server.getInstance().users);
        change.setMessage("remove");


        try {
            Server.getInstance().sendPacketToClients(chatPacket);
            Server.getInstance().sendPacketToClients(change);
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        if(isKick && !socket.isClosed()){
            PrintWriter writer = null;
            try {
                writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(packet.toJSON());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
