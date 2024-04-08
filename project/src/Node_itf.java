import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Map;

public interface Node_itf {
    boolean IsCellFree(String pId, int pX, int pY, int xMax, int xMin, int yMax, int yMin,
            Map<String, Point> map);

    void AskZoneForFreeCell(String pId, String nId, int pX, int pY, int xMax, int xMin, int yMax,
            int yMin, Channel channel);

    void checkHello(Point p, String idP, int xMax, int xMin, int yMax, int yMin, Map<String, Point> map,
            Channel channel) throws IOException;

    void start() throws IOException;

    void processMessage(String message, long deliveryTag) throws IOException;

    void movePlayer(String message, int dx, int dy, Map<String, Point> playersPos,
            int xMax, int xMin, int yMax, int yMin, Channel channel) throws IOException;

    void senMessage(String rootingKey, String message);

    void singleCellCheckHello(String pId, int i, int j, Map<String, Point>map);
}