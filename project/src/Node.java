import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.HashMap;

import java.util.Map;

public class Node {

    private static final String TASK_QUEUE_NAME = "";
    private static final String myId = UUID.randomUUID().toString();
    /*
     * There is three state
     * idle : waits for a start or INIT_HS (HS = handshake)
     * waiting : decide with other worker who starts first
     * started : ping pong has started
     */
    private static String state = "idle";

    public static void main(String[] argv) throws IOException, TimeoutException {
        String task_queue=agrv[0];
        int x_max=atoi(argv[1]);
        int x_min=atoi(argv[2]);
        int y_max=atoi(argv[3]);
        int y_min=atoi(argv[4]);
        Map<String,Point> players_pos=new HashMap<String,Point>();
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();
        channel.queueDeclare(task_queue, false, false, false, null);
        System.out.println(" [*] Queue ready. To exit press CTRL+C");
        // Raquette pro = new Raquette();

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");

            try {
                if (message.startsWith("addPlayer#") && state.equals("ready")) {
                    System.out.println(state);
                    state = "ready";
                    try {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    boolean v;
                    do{
                    v=false;
                    int y=(int)Math.random()*(y_max-y_min)+y_min;
                    int x=(int)Math.random()*(x_max-x_min)+x_min;
                    for(Map.Entry e : players_pos.entrySet()){
                        if(e.getValue().x==x && e.getValue().y==y){
                            v=true ;
                        }
                    }
                    }while(v);
                    players_pos.put(message.substring(10,message.length()-1),new Point(x,y));
                    
                    System.out.println(" [x] Received '" + message + "'");

                } else if (message.startsWith("up#")&&state.equals("ready")) {
                    String id=message.substring(3,message.length());
                    Point pos=players_pos.get(id);
                    pos.x++;
                    if(PosPossible(pos,x_max,x_min,y_max,y_min,players_pos)){
                        players_pos.put(id,pos);
                        checkHello (pos, id, x_max, x_min, y_max, y_min, players_pos, channel);
                    }
                    
                }else if (message.startsWith("down#")&&state.equals("ready")) {
                    String id=message.substring(5,message.length());
                    Point pos=players_pos.get(id);
                    pos.x--;
                    if(PosPossible(pos,x_max,x_min,y_max,y_min,players_pos)){
                        players_pos.put(id,pos);
                        checkHello (pos, id, x_max, x_min, y_max, y_min, players_pos, channel);
                    }
                }
                else if (message.startsWith("right#")&&state.equals("ready")) {
                    String id=message.substring(6,message.length());
                    Point pos=players_pos.get(id);
                    pos.y++;
                    if(PosPossible(pos,x_max,x_min,y_max,y_min,players_pos)){
                        players_pos.put(id,pos);
                        checkHello (pos, id, x_max, x_min, y_max, y_min, players_pos, channel);
                    }
                }
                else if (message.startsWith("left#")&&state.equals("ready")) {
                    String id=message.substring(5,message.length());
                    Point pos=players_pos.get(id);
                    pos.y--;
                    if(PosPossible(pos,x_max,x_min,y_max,y_min,players_pos)){
                        players_pos.put(id,pos);
                        checkHello (pos, id, x_max, x_min, y_max, y_min, players_pos, channel);
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


    private static boolean PosPossible(Point p, int x_max,int x_min, int y_max, int y_min, HashMap map){
        if(p.x>=x_min && p.x<=x_max && p.y>=y_min && p.y<=y_max){
            for(Map.Entry e : map.entrySet()){
                if(e.getValue().x==p.x && e.getValue().y==p.y){
                    return false;
                }
            }
            return true;
        }else{
            return false;
        }
    }
    private static void checkHello (Point p, String id_p, int x_max,int x_min, int y_max, int y_min, HashMap map,Channel channel) throws IOException{
        String res= "list_p#";
        for(int i=p.x-1;i<=p.x+1;i++){
            for(int j=p.y-1;j<=p.y+1;j++){
                if((j!=p.y&&i!=p.x) && i>=x_min && i<=x_max && j>=y_min && j<=y_max){
                for(Map.Entry e : map.entrySet()){
                    if(e.getValue().x==i && e.getValue().y==j){
                        res+=e.getKey()+"#";
                    }
                 }
                }
            }
        }
        channel.basicPublish("", id_p,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                res.getBytes("UTF-8"));
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
