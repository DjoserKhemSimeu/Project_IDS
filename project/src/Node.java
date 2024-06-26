import com.rabbitmq.client.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
    private final String[][] players_Position;

    public Node(String nodeId, int xMax, int xMin, int yMax, int yMin) throws IOException, TimeoutException {
        this.nodeId = nodeId;
        this.xMax = xMax;
        this.xMin = xMin;
        this.yMax = yMax;
        this.yMin = yMin;
        this.playersPos = new HashMap<>();
        this.players_Position = new String[xMax - xMin + 1][yMax - yMin + 1];

        for (int i = 0; i < xMax - xMin + 1; i++) {
            for (int j = 0; j < yMax - yMin + 1; j++) {
                players_Position[i][j] = ".";
            }
        }

        // Initialize RabbitMQ channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        this.channel = connection.createChannel();

        // Declare the task queue
        this.channel.queueDeclare(nodeId, false, false, false, null);
    }

    @Override
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

    @Override
    public void processMessage(String message, long deliveryTag) throws IOException {
        System.out.println(message);
        if (message.startsWith("addPlayer#")) {
            String id = message.substring(10);
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
            playersPos.put(message.substring(10), new Point(x, y));
            this.players_Position[x - xMin][y - yMin] = id;

            String mess = "listofplayer#" + id + x + y + x + y;
            String requestAllPlayer = "requestAll#" + id;
            senMessage("A", mess);
            senMessage("B", mess);
            senMessage("C", mess);
            senMessage("D", mess);
            senMessage("A", requestAllPlayer);
            senMessage("B", requestAllPlayer);
            senMessage("C", requestAllPlayer);
            senMessage("D", requestAllPlayer);
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
            String reqZone = message.substring(7, 8);
            String pId = message.substring(8, 9);
            int xR = Integer.parseInt(message.substring(9, 10));
            int yR = Integer.parseInt(message.substring(10, 11));
            int old_x = Integer.parseInt(message.substring(11, 12));
            int old_y = Integer.parseInt(message.substring(12, 13));
            Boolean answer = IsCellFree(pId, xR, yR, xMax, xMin, yMax, yMin, playersPos, players_Position);

            if (answer) {
                String answerPos = "okSwitch#" + pId;
                senMessage(reqZone, answerPos);
                String changeZone = "changeZone#" + nodeId;
                playersPos.put(pId, new Point(xR, yR));
                senMessage(pId, changeZone);
                String mess = "listofplayer#" + pId + xR + yR + old_x + old_y;
                for (Map.Entry<String, Point> entry : playersPos.entrySet()) {
                    String ndPlayer = entry.getKey();
                    channel.basicPublish("", ndPlayer, MessageProperties.PERSISTENT_TEXT_PLAIN, mess.getBytes("UTF-8"));
                }
                Node_comm_mvmt(mess);
                checkHello(playersPos.get(pId), pId, xMax, xMin, yMax, yMin, playersPos, channel);

            }
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (message.startsWith("okSwitch#")) {

            String pId = message.substring(message.length() - 1);
            Point pos = playersPos.get(pId);
            players_Position[pos.x - xMin][pos.y - yMin] = ".";
            playersPos.remove(pId);
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (message.startsWith("checkHello#")) {
            if (message.length() > 11) {
                String[] parts = message.split("#");
                String pId = parts[1];
                int xCoord = Integer.parseInt(parts[2]);
                int yCoord = Integer.parseInt(parts[3]);
                singleCellCheckHello(pId, xCoord, yCoord, playersPos);

            } else {
            }
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (message.startsWith("listofplayer#")) {
            for (Map.Entry<String, Point> entry : playersPos.entrySet()) {
                String ndPlayer = entry.getKey();
                channel.basicPublish("", ndPlayer, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
                channel.basicAck(deliveryTag, false);

            }
        } else if (message.startsWith("requestAll#")){
            String pId = message.substring(message.length() - 1);
            AllPlayersInZone(pId);
            channel.basicAck(deliveryTag, false);


        }else {
            System.out.println("General received a non-usable message: " + message);
            try {
                channel.basicReject(deliveryTag, false); // Reject and don't requeue
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void Node_comm_mvmt(String message) throws IOException {
        if (this.nodeId.equals("A")) {
            channel.basicPublish("", "B", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
            channel.basicPublish("", "C", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
            channel.basicPublish("", "D", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
        }
        if (this.nodeId.equals("B")) {
            channel.basicPublish("", "A", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
            channel.basicPublish("", "C", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
            channel.basicPublish("", "D", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
        }
        if (this.nodeId.equals("C")) {
            channel.basicPublish("", "B", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
            channel.basicPublish("", "A", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
            channel.basicPublish("", "D", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
        }
        if (this.nodeId.equals("D")) {
            channel.basicPublish("", "B", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
            channel.basicPublish("", "C", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
            channel.basicPublish("", "A", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("UTF-8"));
        }
    }

    @Override
    public boolean IsCellFree(String pId, int pX, int pY, int xMax, int xMin, int yMax, int yMin,
            Map<String, Point> map, String[][] playerpos) {
        if (pX >= xMin && pX <= xMax && pY >= yMin && pY <= yMax) {
            return this.players_Position[pX - xMin][pY - yMin].equals(".");
        } else {
            return false;
        }
    }

    @Override
    public void AskZoneForFreeCell(String pId, String nId, int pX, int pY, int xMax, int xMin, int yMax,
            int yMin, Channel channel, int old_x, int old_y) {
        String message = "askPos#" + nId + pId + pX + pY + old_x + old_y;

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

    @Override
    public void senMessage(String rootingKey, String message) {
        try {
            channel.basicPublish("", rootingKey,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void movePlayer(String message, int dx, int dy, Map<String, Point> playersPos,
            int xMax, int xMin, int yMax, int yMin, Channel channel) throws IOException {
        String id = message.substring(message.indexOf("#") + 1);
        Point pos = playersPos.get(id);
        int x = pos.x + dx;
        int y = pos.y + dy;

        if (x < xMin || x > xMax || y < yMin || y > yMax) {
            AskZoneForFreeCell(id, nodeId, x, y, xMax, xMin, yMax, yMin, channel, pos.x, pos.y);
        } else if (IsCellFree(id, x, y, xMax, xMin, yMax, yMin, playersPos, this.players_Position)) {
            if (pos.x - xMin >= xMin || pos.y - yMin <= yMin) {
                this.players_Position[pos.x - xMin][pos.y - yMin] = ".";
            }

            pos.x += dx;
            pos.y += dy;
            playersPos.put(id, pos);
            this.players_Position[pos.x - xMin][pos.y - yMin] = id;
            String mess = "listofplayer#" + id + pos.x + pos.y + (pos.x - dx) + (pos.y - dy);
            for (Map.Entry<String, Point> entry : playersPos.entrySet()) {
                String ndPlayer = entry.getKey();
                channel.basicPublish("", ndPlayer, MessageProperties.PERSISTENT_TEXT_PLAIN, mess.getBytes("UTF-8"));
            }
            Node_comm_mvmt(mess);
            checkHello(pos, id, xMax, xMin, yMax, yMin, playersPos, channel);
        }
    }

    @Override
    public void singleCellCheckHello(String pId, int i, int j, Map<String, Point> map) {
        String res = "list_p#";
        Boolean isThereNgb = false;
        if (!this.players_Position[i - xMin][j - yMin].equals(".")) {
            isThereNgb = true;
            res += this.players_Position[i - xMin][j - yMin] + "#";
        }
        if (isThereNgb) {
            senMessage(pId, res);
        }

    }

    public void  AllPlayersInZone(String pId){
        String res = "listofplayer";
        for (Map.Entry<String, Point> entry : playersPos.entrySet()) {
            Point point = entry.getValue();
            String ndPlayer = entry.getKey();
            res += "#";
            res += ndPlayer + point.x+point.y+point.x+point.y;
        }
        senMessage(pId, res);
    }

    @Override
    public void checkHello(Point p, String idP, int xMax, int xMin, int yMax, int yMin, Map<String, Point> map,
            Channel channel) throws IOException {
        String res = "list_p#";
        boolean isThereNgb = false;
        for (int i = p.x - 1; i <= p.x + 1; i++) {
            for (int j = p.y - 1; j <= p.y + 1; j++) {
                if (i >= xMin && i <= xMax && j >= yMin && j <= yMax) {
                    if (!(i == p.x && j == p.y)) {
                        if (!this.players_Position[i - xMin][j - yMin].equals(".")) {
                            isThereNgb = true;
                            res += this.players_Position[i - xMin][j - yMin] + "#";

                        }
                    }
                } else if ((i >= 0 && i <= 4) && (j >= 0 && j <= 4)) {
                    String message = "checkHello#" + idP + "#" + i + "#" + j;
                    senMessage("A", message);

                } else if ((i >= 0 && i <= 4) && (j >= 5 && j <= 9)) {
                    String message = "checkHello#" + idP + "#" + i + "#" + j;
                    senMessage("B", message);

                } else if ((i >= 5 && i <= 9) && (j >= 0 && j <= 4)) {
                    String message = "checkHello#" + idP + "#" + i + "#" + j;
                    senMessage("C", message);

                } else if ((i >= 5 && i <= 9) && (j >= 5 && j <= 9)) {
                    String message = "checkHello#" + idP + "#" + i + "#" + j;
                    senMessage("D", message);

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
