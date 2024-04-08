import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.AMQP.Queue.PurgeOk;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class QueueClearExample {


    public static void main(String[] args) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        String QUEUE_NAME = args[0];
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // Purge the queue
            PurgeOk purgeOk = channel.queuePurge(QUEUE_NAME);
            System.out.println("Queue purged successfully. Number of messages removed: " + purgeOk.getMessageCount());
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
}
