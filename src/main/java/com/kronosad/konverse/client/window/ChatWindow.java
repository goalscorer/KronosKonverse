package com.kronosad.konverse.client.window;

import com.google.gson.Gson;
import com.kronosad.konverse.client.App;
import com.kronosad.konverse.client.interfaces.IMessageReceptor;
import com.kronosad.konverse.client.notification.Notification;
import com.kronosad.konverse.common.objects.ChatMessage;
import com.kronosad.konverse.common.objects.PrivateMessage;
import com.kronosad.konverse.common.packets.Packet;
import com.kronosad.konverse.common.packets.Packet02ChatMessage;
import com.kronosad.konverse.common.user.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Created by Russell on 5/24/2014.
 */
public class ChatWindow implements Initializable, IMessageReceptor {
    @FXML
    private TextArea txtAreaMessages;

    @FXML
    private TextField txtToSend;

    @FXML
    private Button btnSend;

    @FXML
    private ListView<Text> userListView;
    private ObservableList<Text> userList = FXCollections.observableArrayList();

    private Map<String, String> colorCaches = new HashMap<String, String>();

    @Override
    public void handleMessage(ChatMessage message) {
        Platform.runLater(() -> {
            if(message.isAction()) {
                appendText("* " + message.getUser().getUsername() + " " + message.getMessage() + "\n");
            }else {
                appendText("[" + message.getUser().getUsername() + "] " + message.getMessage() + "\n");
            }
            if(message.getMessage().contains(App.getInstance().getLocalUser().getUsername())) {
                Notification.Notifier.INSTANCE.notifyInfo("Ping!", String.format("%s said your name in chat!", message.getUser().getUsername()));
            }
        });

    }

    public void appendText(String text) {
        Platform.runLater(() -> txtAreaMessages.appendText(text));
    }

    @Override
    public void handlePrivateMessage(PrivateMessage message) {
        // TODO: Handle private messages :P
        Platform.runLater(() -> appendText("[" + message.getUser().getUsername() + " -> " + App.getInstance().getLocalUser().getUsername() + "] " + message.getMessage() + "\n"));

    }

    @Override
    public void handleUserListChange(List<User> users) {
        Platform.runLater(() -> {
//            userList.clear();
//            users.forEach((user) -> userList.add(getTextForUser(user))) ;
            activateColorUsernames(users);

        });

    }

    public Text getTextForUser(User user) {
        Text text = new Text();
        text.setText(user.getUsername());

        if(user.isElevated()) {
            text.setStyle("-fx-font-weight:bold;");
        }

        return text;
    }

    public void activateColorUsernames(List<User> users) {
        Platform.runLater(userList::clear);
        for (User user : users) {
            Text text = getTextForUser(user);
            if(colorCaches.containsKey(user.getUsername())) {
                text.setFill(Color.web(colorCaches.get(user.getUsername())));
            } else {
                try {
                    InputStream in = new URL("http://kronosad.com:3000/users/" + user.getUsername()).openStream();
                    String JSON = IOUtils.toString(in);
                    if(JSON.startsWith("{")) {
                        ColorProfile profile = new Gson().fromJson(JSON, ColorProfile.class);

                        if (profile.message.equalsIgnoreCase("Color found.")) {
                            text.setFill(Color.web(profile.color));
                            colorCaches.put(user.getUsername(), profile.color);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Platform.runLater(() -> userList.add(text));
        }

    }

    @Override
    public void handleNetworkClosed() {
        // TODO: Handle.
        Platform.runLater(() -> {
            btnSend.setDisable(true);
            txtToSend.setDisable(true);
            txtAreaMessages.setDisable(true);
            userListView.setDisable(true);
        });
        appendText("ERROR: Disconnected!");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        userListView.setItems(userList);

    }

    @FXML
    public void sendMessage(MouseEvent event) {
        send();
    }

    private void send() {
        Platform.runLater(() -> {
            if(txtToSend.getText().length() >= 500) {
                appendText("Error: Message is too big!\n");
                return;
            }

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setMessage(txtToSend.getText());
            chatMessage.setUser(App.getInstance().getLocalUser());

            Packet02ChatMessage chatPacket = new Packet02ChatMessage(Packet.Initiator.CLIENT, chatMessage);

            try {
                App.getInstance().getNetwork().sendPacket(chatPacket);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                txtToSend.clear();
            }

        });

    }

    @FXML
    public void onKeyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            send();
        }
    }
}

class ColorProfile {
    public String message;
    public String color;
}
