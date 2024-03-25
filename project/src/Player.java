import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class Player {

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
        id= argv[0];
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();
        channel.queueDeclare(id, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        // Raquette pro = new Raquette();
        String join="addPlayer#"+id;
        channel.basicPublish("", "A",
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                join.getBytes("UTF-8"));
        String input;
        Scanner scanner=new Scanner(System.in);
        while(true){
                System.out.println("player movement:");
                input=scanner.nextLine();
                String output= input+"#"+id;
                channel.basicPublish("", "A",
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                output.getBytes("UTF-8"));
        }


        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            
            
            try {
                if (message.startsWith("hello#")) {
                    Strind id_s=message.substring(6,message.length()-1);
                    String m="hello#ACK";
                    channel.basicPublish("", id_s,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    m.getBytes("UTF-8"));
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //System.out.println(" [x] Received '" + message + "'");

                    

                } else if (message.startsWith("list_p#")) {
                    String[] ngb = new String[8];
                    String next = message.substring(8,message.length());
                    int i=0;
                    while(next.length()>=1){
                        int sep= next.indexOf('#');
                        ngb[i]=next.substring(0,sep);
                        i++;
                        next=next.substring(sep+1);
                    }
                    sendHello(channel,ngb,i,id);


                    
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
    private static void sendHello (Channel channel, String[]ngb, int size,String id){
        String mess="hello#"+id;
        for (int i=0; i<size;i++){
             channel.basicPublish("", ngb[i],
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                mess.getBytes("UTF-8"));
        }
    }

    private static void handShake(Channel channel) throws IOException {
        String handshake = "INIT_HS#" + myId;
        channel.basicPublish("", TASK_QUEUE_NAME,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                handshake.getBytes("UTF-8"));
        System.out.println(handshake);
    }
}