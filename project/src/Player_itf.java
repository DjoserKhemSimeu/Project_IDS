import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Map;

public interface Player_itf {
    void start() throws IOException;
    void handleMessage(String message, long deliveryTag) throws IOException;
    void sendHello(String[] neighbors) throws IOException;
    void JoinGame() throws IOException;
    void DisplayPlayers(String[][] players) throws IOException;
}