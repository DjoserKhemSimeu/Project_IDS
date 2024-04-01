import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class Node implements Node_itf {
    private final String nodeId;
    private final int xMax;
    private final int xMin;
    private final int yMax;
    private final int yMin;
    private final Map<String, Point> playersPos;
    private final Channel channel;

    public Node(String nodeId, int xMax, int xMin, int yMax, int yMin) throws IOException, TimeoutException {
        this.nodeId = nodeId;
        this.xMax = xMax;
        this.xMin = xMin;
        this.yMax = yMax;
        this.yMin = yMin;
        this.playersPos = new HashMap<>();

        // Initialize RabbitMQ channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        this.channel = connection.createChannel();

        // Declare the task queue
        this.channel.queueDeclare(nodeId, false, false, false, null);
    }

    public void start() throws IOException {
        System.out.println(" [*] Queue ready. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");

            try {
                processMessage(message, delivery.getEnvelope().getDeliveryTag());
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        channel.basicConsume(nodeId, false, deliverCallback, consumerTag -> {
        });
    }

    private void processMessage(String message, long deliveryTag) throws IOException {
        if (message.startsWith("addPlayer#")) {
            String id = message.substring(10);
            // state = "ready";

            boolean v;
            int y;
            int x;
            do {
                v = false;
                y = (int) (Math.random() * (yMax - yMin)) + yMin;
                x = (int) (Math.random() * (xMax - xMin)) + xMin;
                for (Map.Entry<String, Point> entry : playersPos.entrySet()) {
                    Point point = entry.getValue();
                    if (point.x == x && point.y == y) {
                        v = true;
                    }
                }
            } while (v);
            System.out.println(x + " : " + y);
            playersPos.put(message.substring(10), new Point(x, y));
            System.out.println("player '" + id + "' moved to" + playersPos.get(id).Print());

            System.out.println(" [x] Received '" + message + "'");
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // channel.basicAck(deliveryTag false);
        } else if (message.startsWith("up#")) {
            movePlayer(message, -1, 0, playersPos, xMax, xMin, yMax, yMin, channel);
            channel.basicAck(deliveryTag, false);

        } else if (message.startsWith("down#")) {
            movePlayer(message, 1, 0, playersPos, xMax, xMin, yMax, yMin, channel);
            channel.basicAck(deliveryTag, false);

        } else if (message.startsWith("right#")) {
            movePlayer(message, 0, 1, playersPos, xMax, xMin, yMax, yMin, channel);
            channel.basicAck(deliveryTag, false);

        } else if (message.startsWith("left#")) {
            movePlayer(message, 0, -1, playersPos, xMax, xMin, yMax, yMin, channel);
            channel.basicAck(deliveryTag, false);

        } else if (message.startsWith("askPos#")) {
            System.out.println(" [x] Received '" + message + "'");
            String reqZone = message.substring(7, 8);
            String pId = message.substring(8, 9);
            int xR = Integer.parseInt(message.substring(9, 10));
            int yR = Integer.parseInt(message.substring(10));
            System.out.println(reqZone + pId + xR + yR);

            Boolean answer = IsCellOccupied(pId, xR, yR, xMax, xMin, yMax, yMin, playersPos);
            System.out.println(answer);

            if (answer) {
                System.out.println("entered in answer");
                String answerPos = "okSwitch#" + pId;

                senMessage(reqZone, answerPos);
                String changeZone = "changeZone#" + nodeId;
                playersPos.put(pId, new Point(xR, yR));

                senMessage(pId, changeZone);
            }
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (message.startsWith("okSwitch#")) {
            System.out.println(" [x] Received '" + message + "'");

            String pId = message.substring(message.length() - 1);
            System.out.println(pId);
            playersPos.remove(pId);
            for (Map.Entry<String, Point> entry : playersPos.entrySet()) {
                Point point = entry.getValue();
                String ndPlayer = entry.getKey();
                System.out.println("point in map" + point.Print() + " :  player " + ndPlayer);
            }
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            System.out.println("General received a non-usable message: " + message);
            try {
                channel.basicReject(deliveryTag, false); // Reject and don't requeue
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean IsCellOccupied(String pId, int pX, int pY, int xMax, int xMin, int yMax, int yMin,
            Map<String, Point> map) {
        System.out.println(pX + " minX " + xMin);
        System.out.println(pX + " maxX " + xMax);
        System.out.println(pY + " minY " + yMin);
        System.out.println(pY + " maxY " + yMax);

        if (pX >= xMin && pX <= xMax && pY >= yMin && pY <= yMax) {
            System.out.println("hello ?");
            for (Map.Entry<String, Point> entry : map.entrySet()) {
                Point point = entry.getValue();
                String ndPlayer = entry.getKey();
                System.out.println("point in map" + point.Print() + " :  player " + ndPlayer);

                if (point.x == pX && point.y == pY && !ndPlayer.equals(pId)) {
                    System.out.println("inside if | " + ndPlayer + " vs " + pId);

                    System.out.println(entry);

                    return false;
                }
            }
            System.out.println("true");
            return true;
        } else {
            return false;
        }
    }

    public void AskZoneForFreeCell(String pId, String nId, int pX, int pY, int xMax, int xMin, int yMax,
            int yMin, Channel channel) {
        String message = "askPos#" + nId + pId + pX + pY;

        if ((pX >= 0 && pX <= 4) && (pY >= 0 && pY <= 4)) {
            senMessage("A", message);

        } else if ((pX >= 0 && pX <= 4) && (pY >= 5 && pY <= 9)) {
            senMessage("B", message);

        } else if ((pX >= 5 && pX <= 9) && (pY >= 0 && pY <= 4)) {
            senMessage("C", message);

        } else if ((pX >= 5 && pX <= 9) && (pY >= 5 && pY <= 9)) {
            senMessage("D", message);

        }
    }
    private void senMessage(String rootingKey, String message){
        try {
            channel.basicPublish("", rootingKey,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void movePlayer(String message, int dx, int dy, Map<String, Point> playersPos,
            int xMax, int xMin, int yMax, int yMin, Channel channel) throws IOException {
        String id = message.substring(message.indexOf("#") + 1);
        Point pos = playersPos.get(id);
        int x = pos.x + dx;
        int y = pos.y + dy;

        if (x < xMin || x > xMax || y < yMin || y > yMax) {
            AskZoneForFreeCell(id, nodeId, x, y, xMax, xMin, yMax, yMin, channel);
        } else if (IsCellOccupied(id, x, y, xMax, xMin, yMax, yMin, playersPos)) {
            pos.x += dx;
            pos.y += dy;
            playersPos.put(id, pos);
            checkHello(pos, id, xMax, xMin, yMax, yMin, playersPos, channel);
        }

        System.out.println(" [x] Received '" + message + "'");
        System.out.println("player '" + id + "' moved to" + playersPos.get(id).Print());
    }

    public void checkHello(Point p, String idP, int xMax, int xMin, int yMax, int yMin, Map<String, Point> map,
            Channel channel) throws IOException {
        String res = "list_p#";
        boolean isThereNgb = false;
        for (int i = p.x - 1; i <= p.x + 1; i++) {
            for (int j = p.y - 1; j <= p.y + 1; j++) {
                if (i >= xMin && i <= xMax && j >= yMin && j <= yMax) {
                    System.out.println("x : " + i + ", y : " + j);

                    if (!(i == p.x && j == p.y)) {
                        for (Map.Entry<String, Point> entry : map.entrySet()) {
                            Point point = entry.getValue();
                            if (point.x == i && point.y == j) {
                                isThereNgb = true;

                                System.out.println("hello ???");
                                res += entry.getKey() + "#";
                                System.out.println(res);
                            }
                        }
                    }
                }
            }
        }
        if (isThereNgb) {
            senMessage(idP, res);
        }
    }

    public static void main(String[] argv) throws IOException, TimeoutException {
        if (argv.length != 5) {
            System.out.println("wrong usage of the process, please use :");
            System.out.println("Node.java Node_name max_x min_x max_y min_y");
            System.exit(0);
        }

        String nodeId = argv[0];
        int xMax = Integer.parseInt(argv[1]);
        int xMin = Integer.parseInt(argv[2]);
        int yMax = Integer.parseInt(argv[3]);
        int yMin = Integer.parseInt(argv[4]);

        Node node = new Node(nodeId, xMax, xMin, yMax, yMin);
        node.start();
    }
}
