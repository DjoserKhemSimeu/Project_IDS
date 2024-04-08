import com.rabbitmq.client.*;

public class QueueDeletionExample {

    private static final String QUEUE_NAME = "task_queue";

    public static void main(String[] argv) throws Exception {
        // Create a connection factory
        ConnectionFactory factory = new ConnectionFactory();
        // Set the RabbitMQ server host
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // Delete the queue named "your_queue_name"
            channel.queueDelete(QUEUE_NAME);
            System.out.println("Queue deleted successfully.");
        }
    }
}
