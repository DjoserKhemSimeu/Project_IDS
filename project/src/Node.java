import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.HashMap;
import java.util.Map;

public class Node {

    // private static final String myId = UUID.randomUUID().toString();
    private static String state = "ready";

    public static void main(String[] argv) throws IOException, TimeoutException {
        if (argv.length != 5) {
            System.out.println("wrong usage of the process, please use :");
            System.out.println("Node.java Node_name max_x min_x max_y min_y");
            System.exit(0);
        }
        String taskQueue = argv[0];
        String nId = taskQueue;
        System.out.println(argv[0]);
        int xMax = Integer.parseInt(argv[1]);
        int xMin = Integer.parseInt(argv[2]);
        int yMax = Integer.parseInt(argv[3]);
        int yMin = Integer.parseInt(argv[4]);
        System.out.println(xMax + " " + xMin + ";" + yMax + " " + yMin);

        Map<String, Point> playersPos = new HashMap<>();
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();
        channel.queueDeclare(taskQueue, false, false, false, null);
        System.out.println(" [*] Queue ready. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");

            try {
                if (message.startsWith("addPlayer#") && state.equals("ready")) {
                    String id = message.substring(10);
                    System.out.println(state);
                    // state = "ready";
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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
                    System.out.println("; player '" + id + "' moved to" + playersPos.get(id).Print());

                    System.out.println(" [x] Received '" + message + "'");
                    // channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } else if (message.startsWith("up#") && state.equals("ready")) {
                    System.out.println(" [x] before any manip '" + message + "'");

                    String id = message.substring(3);
                    Point pos = playersPos.get(id);
                    int x = pos.x - 1;
                    int y = pos.y;
                    if (IsCellOccupied(id, x, y, xMax, xMin, yMax, yMin, playersPos)) {
                        pos.x--;

                        playersPos.put(id, pos);
                        checkHello(pos, id, xMax, xMin, yMax, yMin, playersPos, channel);
                    }
                    System.out.println(" [x] Received '" + message + "'");
                    System.out.println("; player '" + id + "' moved to" + playersPos.get(id).Print());
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } else if (message.startsWith("down#") && state.equals("ready")) {
                    String id = message.substring(5);
                    Point pos = playersPos.get(id);
                    int x = pos.x + 1;
                    int y = pos.y;
                    if (x > xMax) {

                        AskZoneForFreeCell(id, nId, x, y, xMax, xMin, yMax, yMin, channel);

                    } else if (IsCellOccupied(id, x, y, xMax, xMin, yMax, yMin, playersPos)) {
                        pos.x++;
                        playersPos.put(id, pos);
                        checkHello(pos, id, xMax, xMin, yMax, yMin, playersPos, channel);
                    }
                    System.out.println(" [x] Received '" + message + "'");
                    System.out.println("; player '" + id + "' moved to" + playersPos.get(id).Print());
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } else if (message.startsWith("right#") && state.equals("ready")) {
                    String id = message.substring(6);
                    Point pos = playersPos.get(id);
                    int x = pos.x;
                    int y = pos.y + 1;
                    if (y > yMax) {

                        AskZoneForFreeCell(id, nId, x, y, xMax, xMin, yMax, yMin, channel);

                    } else if (IsCellOccupied(id, x, y, xMax, xMin, yMax, yMin, playersPos)) {
                        pos.y++;

                        playersPos.put(id, pos);
                        checkHello(pos, id, xMax, xMin, yMax, yMin, playersPos, channel);
                    }
                    System.out.println(" [x] Received '" + message + "'");
                    System.out.println("; player '" + id + "' moved to" + playersPos.get(id).Print());
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } else if (message.startsWith("left#") && state.equals("ready")) {
                    String id = message.substring(5);
                    Point pos = playersPos.get(id);
                    int x = pos.x;
                    int y = pos.y - 1;
                    if (IsCellOccupied(id, x, y, xMax, xMin, yMax, yMin, playersPos)) {
                        pos.y--;

                        playersPos.put(id, pos);
                        checkHello(pos, id, xMax, xMin, yMax, yMin, playersPos, channel);
                    }
                    System.out.println(" [x] Received '" + message + "'");
                    System.out.println("; player '" + id + "' moved to" + playersPos.get(id).Print());
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
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

                        channel.basicPublish("", reqZone,
                                MessageProperties.PERSISTENT_TEXT_PLAIN,
                                answerPos.getBytes("UTF-8"));
                        String changeZone = "changeZone#"+nId;
                        playersPos.put(pId, new Point(xR, yR));

                        channel.basicPublish("", pId,
                                MessageProperties.PERSISTENT_TEXT_PLAIN,
                                changeZone.getBytes("UTF-8"));
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

                } else {
                    System.out.println("General received a non-usable message: " + message);
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false); // Reject and requeue
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        channel.basicConsume(taskQueue, false, deliverCallback, consumerTag -> {
        });
    }

    private static boolean IsCellOccupied(String pId, int pX, int pY, int xMax, int xMin, int yMax, int yMin,
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

    private static void AskZoneForFreeCell(String pId, String nId, int pX, int pY, int xMax, int xMin, int yMax,
            int yMin, Channel channel) {
        if (pY > yMax) {
            String message = "askPos#" + nId + pId + pX + pY;
            try {
                channel.basicPublish("", "B",
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        message.getBytes("UTF-8"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void checkHello(Point p, String idP, int xMax, int xMin, int yMax, int yMin, Map<String, Point> map,
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
            channel.basicPublish("", idP,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    res.getBytes("UTF-8"));
        }
    }
}
