import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class Player implements Player_itf {
    private final String playerId;
    private String currentZone;
    private final Channel channel;
    private final Scanner scanner;
    private final String[] input;
    private String[][] Players;

    public Player(String playerId, String currentZone, int height_Zone, int width_Zone) throws IOException, TimeoutException {
        // initializing attributes
        this.playerId = playerId;
        this.currentZone = currentZone;

        this.input = new String[1]; // used to store player movement request

        this.Players = new String[width_Zone][height_Zone];
        for (int i = 0; i < 10; i++){
            for (int j = 0; j < 10; j++){
                this.Players[i][j] = ".";
            }
        }
        // initializing rabbitMQ channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        this.channel = connection.createChannel();
        channel.queueDeclare(playerId, false, false, false, null);

        this.scanner = new Scanner(System.in);
    }

    @Override
    public void JoinGame() throws IOException {
        String join = "addPlayer#" + this.playerId;
        try {
            this.channel.basicPublish("", this.currentZone,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    join.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start() throws IOException {
        this.JoinGame();
        System.out.println(
                "Welcome in the Hello world, to move arround the world type 'up', 'down', 'left' or 'right'\n");
        System.out.println("Have fun greeting people ! :)\n");

        Thread inputThread = new Thread(() -> {
            while (true) {
                System.out.println("player movement:");
                input[0] = scanner.nextLine(); // Modify the value in the array
                try {
                    String output = input[0] + "#" + playerId;
                    channel.basicPublish("", this.currentZone,
                            MessageProperties.PERSISTENT_TEXT_PLAIN,
                            output.getBytes("UTF-8"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        inputThread.start();

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            handleMessage(message, delivery.getEnvelope().getDeliveryTag());
        };

        Thread callbackThread = new Thread(() -> {
            try {
                channel.basicConsume(playerId, false, deliverCallback, consumerTag -> {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        callbackThread.start();
    }

    @Override
    public void handleMessage(String message, long deliveryTag) throws IOException {
        try {
            System.out.println(" message received : " + message);

            if (message.startsWith("hello#")) {
                String senderId = message.substring(6);
                System.out.println("From " + senderId + ": Hello.");
                channel.basicPublish("", senderId, MessageProperties.PERSISTENT_TEXT_PLAIN,
                        ("helloACK#" + playerId).getBytes("UTF-8"));
                System.out.println("To " + senderId + ": Hello.");
                channel.basicAck(deliveryTag, false);
            } else if (message.startsWith("helloACK#")) {
                System.out.println("From player " + message.substring(message.indexOf("#") + 1) + ": Hello.");
                channel.basicAck(deliveryTag, false);
            } else if (message.startsWith("list_p#")) {
                String[] neighbors = message.substring(7).split("#");
                sendHello(neighbors);
                channel.basicAck(deliveryTag, false);
            } else if (message.startsWith("changeZone#")) {
                this.currentZone = message.substring(message.length() - 1);
                channel.basicAck(deliveryTag, false);
            } else if (message.startsWith("listofplayer#")){
                // System.out.println("hello ?");
                PlayerListParser(message);
                channel.basicAck(deliveryTag, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendHello(String[] neighbors) throws IOException {
        String helloMessage = "hello#" + playerId;
        for (String neighbor : neighbors) {
            channel.basicPublish("", neighbor, MessageProperties.PERSISTENT_TEXT_PLAIN, helloMessage.getBytes("UTF-8"));
            System.out.println("To player " + neighbor + ": Hello.");
        }
    }

    public static void main(String[] argv) throws IOException, TimeoutException {
        if (argv.length != 2) {
            System.out.println("Usage: Player <playerId> <zoneId>");
            System.exit(1);
        }

        String playerId = argv[0];
        String zoneId = argv[1];

        Player player = new Player(playerId, zoneId, 10, 10);
        player.start();
    }

    @Override
    public void DisplayPlayers(String[][] players) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'DisplayPlayers'");
    }

    public void PlayerListParser(String message) {
        parsePlayerList(message, this.Players);

        // // Print the player grid
        // for (String[] row : this.Players) {
        //     for (String playerId : row) {
        //         System.out.print(playerId + "\t");
        //     }
        //     System.out.println();
        // }
    }

    public static void parsePlayerList(String message, String[][] Players) {
        String[] parts = message.split("#");
        String playerIDD;
        int x;
        int y;
        int old_x;
        int old_y;
        // System.out.println("player : " + playerIDD + "x : " + x + " y : " + y);
        for (int i = 1; i < parts.length; i++){
            x = Integer.parseInt(parts[i].substring(1, 2));
            y = Integer.parseInt(parts[i].substring(2, 3));
            old_x = Integer.parseInt(parts[i].substring(3, 4));
            old_y = Integer.parseInt(parts[i].substring(4, 5));
            
            playerIDD = parts[i].substring(0, 1);
            Players[x][y] = playerIDD;
            Players[old_x][old_y] = ".";
        }
        System.out.println("|\n-----------------------------------------");
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 10; col++) {
                if (col == 5){
                    System.out.print("║ " + Players[row][col] + " ");
                }else {
                    System.out.print("| " + Players[row][col] + " ");

                }
            }
            if (row == 4){
                System.out.println("|\n=========================================");

            }else {
                System.out.println("|\n-----------------------------------------");

            }

        }
    }
}
