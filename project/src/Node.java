import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class Raquette {

    private static final String TASK_QUEUE_NAME = "task_queue";
    private static final String myId = UUID.randomUUID().toString();
    /*
     * There is three state
     * idle : waits for a start or INIT_HS (HS = handshake)
     * waiting : decide with other worker who starts first
     * started : ping pong has started
     */
    private static String state = "idle";

    public static void main(String[] argv) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();
        channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        // Raquette pro = new Raquette();

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");

            try {
                if (message.equals("start") && state.equals("idle")) {
                    System.out.println(state);
                    state = "waiting";
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println(" [x] Received '" + message + "'");

                    handShake(channel);

                } else if (message.length() > 9
                        &&
                        (message.substring(0, 7).equals("INIT_HS") && state.equals("waiting")
                                ||
                                message.substring(0, 7).equals("INIT_HS") && state.equals("idle"))) {
                    String senderId = message.substring(8, message.length() - 1);
                    if (myId.compareTo(senderId) > 0) {
                        try {
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (state.equals("idle")) {
                            handShake(channel);
                        }
                        state = "pong";
                        System.out.println(state + " higher");
                        System.out.println(" [x] Received '" + message + "'");

                    } else if (myId.compareTo(senderId) < 0) {
                        try {
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (state.equals("idle")) {
                            handShake(channel);
                        }

                        state = "ping";
                        System.out.println(state + " lower");
                        System.out.println(" [x] Received '" + message + "'");

                        sendPing(channel);

                    }  else if (myId.equals(senderId)){
                        System.out.println(" handshake recieved a non usable message : " + message);
                        try {
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

                            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), true); // Reject and requeue
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (message.equals("ping") && state.equals("pong")) {
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println(message + "   ###   " + state);
                    System.out.println(" [x] Received '" + message + "'");

                    sendPong(channel);

                } else if (message.equals("pong") && state.equals("ping")) {
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println(message + "   &&&   " + state);
                    System.out.println(" [x] Received '" + message + "'");

                    sendPing(channel);

                } else {
                    System.out.println("general recieved a non usable message : " + message);
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

                        channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false); // Reject and requeue
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // System.out.println(" [x] Done");
            }

        };

        channel.basicConsume(TASK_QUEUE_NAME, false, deliverCallback, consumerTag -> {
        });
    }

    private static void sendPing(Channel channel) throws IOException {
        String message = "ping";
        channel.basicPublish("", TASK_QUEUE_NAME,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes("UTF-8"));
        System.out.println(" [x] Sent '" + message + "'");
    }

    private static void sendPong(Channel channel) throws IOException {
        String message = "pong";
        channel.basicPublish("", TASK_QUEUE_NAME,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes("UTF-8"));
        System.out.println(" [x] Sent '" + message + "'");
    }

    private static void handShake(Channel channel) throws IOException {
        String handshake = "INIT_HS#" + myId;
        channel.basicPublish("", TASK_QUEUE_NAME,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                handshake.getBytes("UTF-8"));
        System.out.println(handshake);
    }
}