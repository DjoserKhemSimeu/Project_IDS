import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class Player {

    // private static final String TASK_QUEUE_NAME = "1";
    // private static final String myId = UUID.randomUUID().toString();
    // private static String state = "idle";

    public static void main(String[] argv) throws IOException, TimeoutException {
        String id = argv[0];
        String taskQueue = id;
        // static String zone = argv[1];
        final String[] zone = new String[1];

        zone[0] = argv[1];  
        System.out.println("hello");



        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();
        channel.queueDeclare(id, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        String join = "addPlayer#" + id;
        channel.basicPublish("", zone[0],
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                join.getBytes("UTF-8"));
        final String[] input = new String[1];
        Scanner scanner = new Scanner(System.in);

        // Create a new thread for the while loop
        Thread inputThread = new Thread(() -> {
            while (true) {
                System.out.println("player movement:");
                input[0] = scanner.nextLine(); // Modify the value in the array
                try {
                    String output = input[0] + "#" + id;
                    channel.basicPublish("", zone[0],
                            MessageProperties.PERSISTENT_TEXT_PLAIN,
                            output.getBytes("UTF-8"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Start the input thread
        inputThread.start();

        // Add shutdown hook to close resources when the program exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scanner.close(); // Close the scanner when program exits
        }));
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [x] Received '" + message + "'");

            try {
                if (message.startsWith("hello#")) {
                    String idS = message.substring(6);
                    String m = "hello#ACK";
                    channel.basicPublish("", idS,
                            MessageProperties.PERSISTENT_TEXT_PLAIN,
                            m.getBytes("UTF-8"));
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (message.startsWith("list_p#")) {
                    String[] ngb = new String[8];
                    String next = message.substring(7);
                    int i = 0;
                    while (next.length() >= 1) {
                        int sep = next.indexOf('#');
                        ngb[i] = next.substring(0, sep);
                        i++;
                        next = next.substring(sep + 1);
                    }
                    sendHello(channel, ngb, i, id);
                } else if (message.startsWith("changeZone#")){
                    zone[0] = message.substring(message.length()-1);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        Thread callbackThread = new Thread(() -> {

            try {
                channel.basicConsume(taskQueue, false, deliverCallback, consumerTag -> {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        callbackThread.start();

    }

    private static void sendHello(Channel channel, String[] ngb, int size, String id) throws IOException {
        String mess = "hello#" + id;
        for (int i = 0; i < size; i++) {
            channel.basicPublish("", ngb[i],
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    mess.getBytes("UTF-8"));
        }
    }

}
