package tech.lity.rea.nectar;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import processing.core.*;
import processing.data.JSONArray;
import processing.data.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import tech.lity.rea.javacvprocessing.ProjectiveDeviceP;
import static tech.lity.rea.javacvprocessing.ProjectiveDeviceP.PMatrixToJSON;
import tech.lity.rea.nectar.markers.MarkerList;
import tech.lity.rea.nectar.camera.Camera;
import tech.lity.rea.nectar.camera.CameraNectar;
import tech.lity.rea.nectar.tracking.MarkerBoard;
import tech.lity.rea.nectar.tracking.MarkerBoardFactory;

/**
 *
 * @author Jeremy Laviole, <laviole@rea.lity.tech>
 */
@SuppressWarnings("serial")
public class PoseEstimator {

    static Jedis redis;
    static Jedis redisSend;

    // TODO: add hostname ?
    public static final String OUTPUT_PREFIX = "nectar:";
    public static final String OUTPUT_PREFIX2 = ":camera-server:camera";

    public static final String REDIS_PORT = "6379";
    public static final String REDIS_HOST = "localhost";

    static private String pathName = "";
    static private String cameraFileName = "";
    static private String cameraName = "";
    static private String markerFileName = "";
    static private String input = "marker";
    static private String output = "pose";
    static private String host = REDIS_HOST;
    static private String port = REDIS_PORT;
    static private boolean isUnique = false;
    private static boolean isStreamSet = false;

    static String defaultHost = "jiii-mi";
    static String defaultName = OUTPUT_PREFIX + defaultHost + OUTPUT_PREFIX2 + "#0";

    static ProjectiveDeviceP cameraDevice;
    static MarkerList markersFromSVG;

    static private CameraNectar cam;

    // TODO: Get the camera calibration from Redis.
    public static void die(String why) {
        die(why, false);
    }

    public static void die(String why, boolean usage) {
        if (usage) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("PoseEstimator", options);
        }
        System.out.println(why);
        System.exit(-1);
    }

    static boolean isVerbose = false;
    static boolean isSilent = false;

    public static void log(String normal, String verbose) {

        if (isSilent) {
            return;
        }
        if (normal != null) {
            System.out.println(normal);
        }
        if (isVerbose) {
            System.out.println(verbose);
        }
    }
    static Options options = new Options();
    static MarkerBoard board;

    static public void main(String[] passedArgs) {

        options = new Options();
        options.addRequiredOption("i", "input", true, "Camera input key .");
        options.addRequiredOption("cc", "camera-configuration", true, "Camera calibration file.");
        options.addRequiredOption("mc", "marker-configuration", true, "Marker configuration file.");
        options.addOption("p", "path", true, "Optionnal path.");

        options.addOption("s", "stream", false, " stream mode (PUBLISH).");
        options.addOption("sg", "stream-set", false, " stream mode (SET).");
        options.addOption("u", "unique", false, "Unique mode, run only once and use get/set instead of pub/sub");

        // Generic options
        options.addOption("h", "help", false, "print this help.");
        options.addOption("v", "verbose", false, "Verbose activated.");
        options.addOption("s", "silent", false, "Silent activated.");
        options.addOption("u", "unique", false, "Unique mode, run only once and use get/set instead of pub/sub");
        options.addRequiredOption("o", "output", true, "Output key.");
        options.addOption("rp", "redisport", true, "Redis port, default is: " + REDIS_PORT);
        options.addOption("rh", "redishost", true, "Redis host, default is: " + REDIS_HOST);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        // -u -i markers -cc data/calibration-AstraS-rgb.yaml -mc data/A4-default.svg -o pose
        try {
            cmd = parser.parse(options, passedArgs);
            if (cmd.hasOption("i")) {
                cameraName = cmd.getOptionValue("i");
            }

            if (cmd.hasOption("cc")) {
                cameraFileName = cmd.getOptionValue("cc");
            }
            if (cmd.hasOption("mc")) {
                markerFileName = cmd.getOptionValue("mc");
            }

            if (cmd.hasOption("h")) {
                die("", true);
            }

            if (cmd.hasOption("o")) {
                output = cmd.getOptionValue("o");
            } else {
                die("Please set an output key with -o or --output ", true);
            }

            if (cmd.hasOption("p")) {
                pathName = cmd.getOptionValue("p");
            }

            if (cmd.hasOption("sg")) {
                isStreamSet = true;
            }
            if (cmd.hasOption("u")) {
                isUnique = true;
            }
            if (cmd.hasOption("v")) {
                isVerbose = true;
            }
            if (cmd.hasOption("s")) {
                isSilent = true;
            }
            if (cmd.hasOption("rh")) {
                host = cmd.getOptionValue("rh");
            }
            if (cmd.hasOption("rp")) {
                port = cmd.getOptionValue("rp");
            }
        } catch (ParseException ex) {
            die(ex.toString(), true);
        }
        connectRedis();
        
        try {
            Path currentRelativePath = Paths.get(pathName);
            String path = currentRelativePath.toAbsolutePath().toString();
            // Only rgb camera can track markers for now.
            cam = new CameraNectar(cameraName);
            cam.setCalibration(path + "/" + cameraFileName);
            
            cam.DEFAULT_REDIS_HOST = host;
            cam.DEFAULT_REDIS_PORT = Integer.parseInt(port);
            
            if (isStreamSet) {
                cam.setGetMode(isStreamSet);
            }           
            cam.start();
//            board = MarkerBoardFactory.create(path + "/" + markerFileName);
            board = MarkerBoardFactory.create(path + "/" + markerFileName);
            cam.addObserver(new ImageObserver());
//            cam.track(board);
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(PoseEstimator.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Waiting for something.");
    }

    static class ImageObserver implements Observer {

        @Override
        public void update(Observable o, Object o1) {
            board.updateLocation(cam, cam.getIplImage(), null);
            PMatrix3D position = board.getPosition(cam);

            JSONObject matrix = new JSONObject();
            JSONArray poseJson = PMatrixToJSON(position);

            matrix.setJSONArray("matrix", poseJson);
            if (isVerbose) {
                position.print();
            }
            if (isStreamSet) {
                redisSend.set(output, matrix.toString());
            } else {
                redisSend.publish(output, matrix.toString());
            }
//            Markerboard update... send pose...
        }

    }

    static void sendPose(String message, boolean set) {

        PMatrix3D pose = new PMatrix3D();

        if (pose == null) {
            log("Cannot find pose " + message, "");
            return;
        }
        JSONArray poseJson = PMatrixToJSON(pose);
        if (set) {
            redisSend.set(output, poseJson.toString());
            log("Pose set to " + output, " set " + poseJson.toString());
        } else {
            redisSend.publish(output, poseJson.toString());
            log("Pose updated to " + output, "published " + poseJson.toString());
        }
    }

    static String markersChannels = "custom:image:detected-markers";

    static class MyListener extends JedisPubSub {

        // Listen to "camera
        @Override
        public void onMessage(String channel, String message) {

            log(null, "received " + message);
            sendPose(message, false);
        }

        @Override
        public void onSubscribe(String channel, int subscribedChannels) {
            System.out.println("Subscribe to: " + channel);
        }

        @Override
        public void onUnsubscribe(String channel, int subscribedChannels) {
            System.out.println("CHANNEL: " + channel);
        }

        public void onPSubscribe(String pattern, int subscribedChannels) {
        }

        public void onPUnsubscribe(String pattern, int subscribedChannels) {

        }

        @Override
        public void onPMessage(String pattern, String channel, String message) {
            System.out.println("CHANNEL: " + channel);
        }
    }

    private static void connectRedis() {
        try {
            redis = new Jedis(host, Integer.parseInt(port));
            redisSend = new Jedis(host, Integer.parseInt(port));
            if (redis == null) {
                throw new Exception("Cannot connect to server. ");
            }
            else {
                System.out.println("PoseEstimator - Connected to Redis.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        // redis.auth("156;2Asatu:AUI?S2T51235AUEAIU");
    }
}
