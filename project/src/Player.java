import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class Player {

    // private static final String TASK_QUEUE_NAME = "1";
    // private static final String myId = UUID.randomUUID().toString();
    // private static String state = "idle";

    public static void main(String[] argv) throws IOException, TimeoutException {
        String id = argv[0];
        String taskQueue = id;
        String zone = argv[1];

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();
        channel.queueDeclare(id, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        String join = "addPlayer#" + id;
        channel.basicPublish("", zone,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                join.getBytes("UTF-8"));
        final String[] input = new String[1];
        Scanner scanner = new Scanner(System.in);


        Thread inputThread = new Thread(() -> {
            while (true) {
                System.out.println("player movement:");
                input[0] = scanner.nextLine(); 
                try {
                    String output = input[0] + "#" + id;
                    channel.basicPublish("", zone,
                            MessageProperties.PERSISTENT_TEXT_PLAIN,
                            output.getBytes("UTF-8"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

 
        inputThread.start();

 
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scanner.close(); 
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
