import com.google.gson.Gson;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.swagger.client.model.LiftRide;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.concurrent.EventCountCircuitBreaker;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@WebServlet(name = "SkierServlet", value = "/SkierServlet")
public class SkierServlet extends HttpServlet {

    private static final int NUM_CHANS = 100;
    private static final int WAIT_TIME_SECS = 1;
    private static final String QUEUE_NAME = "skiersInfo";
    private static final String SERVER = "localhost";
    private GenericObjectPool<Channel> pool;
    private JedisPool jedisPool;
    EventCountCircuitBreaker breaker = new EventCountCircuitBreaker(1000, 1, TimeUnit.SECONDS, 800);

    public void init() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(SERVER);
        factory.setUsername("yutingz");
        factory.setPassword("yutingz");
        factory.setVirtualHost("vhost");
        factory.setPort(5672);
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(200);
        jedisPool = new JedisPool(poolConfig, "localhost", 6379);

        final Connection conn;
        try {
            conn = factory.newConnection();
            GenericObjectPoolConfig config = new GenericObjectPoolConfig();
            config.setMaxTotal(NUM_CHANS);
            config.setBlockWhenExhausted(true);
            config.setMaxWait(Duration.ofSeconds(WAIT_TIME_SECS));
            RMQChannelFactory channelFactory = new RMQChannelFactory(conn);
            pool = new GenericObjectPool<>(channelFactory, config);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String urlPath = request.getPathInfo();
        if (urlPath == null || urlPath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("missing parameters");
            return;
        }

        String[] urlParts = urlPath.split("/");
        if (!isUrlValid(urlParts)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else {
            if (urlPath.length() == 7 && urlParts[6].equals("skiers")) {
                try (Jedis jedis = jedisPool.getResource()) {
                    response.getWriter().write(String.valueOf(
                            jedis.scard("resortId:" + urlParts[1] + "seasonId:" + urlParts[3] + "dayId:" + urlParts[5])));
                }
            } else if (urlPath.length() == 8) {
                try (Jedis jedis = jedisPool.getResource()) {
                    response.getWriter().write(jedis.get("resortId:" + urlParts[1] + "seasonId:" + urlParts[3] +
                            "dayId:" + urlParts[5] + "skierId:" + urlParts[7] + "vertical:"));
                }
            } else if (urlPath.length() == 5) {
                try (Jedis jedis = jedisPool.getResource()) {
                    response.getWriter().write(jedis.get("resortId:" + urlParts[3] + "skierId:" + urlParts[1] + "vertical:"));
                }
            } else if (urlPath.length() == 3) {
                try (Jedis jedis = jedisPool.getResource()) {
                    response.getWriter().write(jedis.get("skierId:" + urlParts[1] + "vertical:"));
                }
            } else if (urlPath.length() == 7 && urlParts[6].equals("vertical")) {
                try (Jedis jedis = jedisPool.getResource()) {
                    response.getWriter().write(jedis.get("resortId:" + urlParts[3] + "seasonId:" + urlParts[5] +
                            "skierId:" + urlParts[1] + "vertical:"));
                }
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        Gson gson = new Gson();
        String urlPath = request.getPathInfo();

        // check we have a URL!
        if (urlPath == null || urlPath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("missing parameters");
            return;
        }

        String[] urlParts = urlPath.split("/");
        if (!isUrlValid(urlParts)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else {
            LiftRide liftRide = gson.fromJson(IOUtils.toString(request.getReader()), LiftRide.class);
            RequestData requestData = new RequestData(
                    Integer.parseInt(urlParts[1]),
                    urlParts[3],
                    urlParts[5],
                    Integer.parseInt(urlParts[7]),
                    liftRide);
            String payload = gson.toJson(requestData);

            if (breaker.incrementAndCheckState()) {
                try {
                    Channel channel = pool.borrowObject();
                    if (channel != null) {
                        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                        channel.basicPublish("", QUEUE_NAME, null, payload.getBytes(StandardCharsets.UTF_8));
                        pool.returnObject(channel);
                        response.setStatus(HttpServletResponse.SC_CREATED);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            }
        }
    }

    private boolean isUrlValid(String[] urlParts) {
        if (urlParts.length == 8) {
            int dayId = Integer.parseInt(urlParts[5]);
            if (!isInteger(urlParts[1]) || !isInteger(urlParts[7]) || dayId < 1 || dayId > 366) {
                return false;
            }
            return urlParts[2].equals("seasons") && urlParts[4].equals("days") && urlParts[6].equals("skiers");
        } else if (urlParts.length == 3) {
            if (!isInteger(urlParts[1])) {
                return false;
            }
            return urlParts[2].equals("vertical");
        } else if (urlParts.length == 5) {
            if (!isInteger(urlParts[1])) {
                return false;
            }
            return urlParts[2].equals("resorts") && urlParts[4].equals("vertical");
        } else if (urlParts.length == 7) {
            if (urlParts[2].equals("seasons") && urlParts[4].equals("day") && urlParts[6].equals("skiers")) {
                return isInteger(urlParts[1]);
            } else if (urlParts[2].equals("resorts") && urlParts[4].equals("seasons") && urlParts[6].equals("vertical")){
                return isInteger(urlParts[1]);
            }
        }
        return false;
    }

    private boolean isInteger(String val) {
        try {
            Integer.parseInt(val);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

