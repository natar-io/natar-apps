package tech.lity.rea.nectar;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import processing.core.*;
import processing.data.JSONArray;
import processing.data.JSONObject;
import redis.clients.jedis.Jedis;
import tech.lity.rea.javacvprocessing.ProjectiveDeviceP;
import static tech.lity.rea.javacvprocessing.ProjectiveDeviceP.PMatrixToJSON;
import tech.lity.rea.nectar.camera.CameraNectar;
import tech.lity.rea.nectar.markers.MarkerList;
import tech.lity.rea.nectar.tracking.MarkerBoard;
import tech.lity.rea.nectar.tracking.MarkerBoardSvg;

/**
 *
 * @author Jeremy Laviole, <laviole@rea.lity.tech>
 */
@SuppressWarnings("serial")
public class MultiPoseEstimator extends NectarApplication {

    static private Jedis redis;

    static private String cameraName = "";

    static ProjectiveDeviceP cameraDevice;
    static MarkerList markersFromSVG;

    static private CameraNectar cam;
    static final private ArrayList<MarkerBoard> markerboards = new ArrayList<>();

    static public void main(String[] passedArgs) {

        parseCLI(passedArgs);

        redis = connectRedis();
        try {
            /**
             * 1. Load all MarkerBoards. 2. Substribe to markers. 3. When a
             * markerList is received -> Find the pose if possible. 4. Send
             * (publish & Set) new location.
             */
            // Subscribe to markers 
            // Only rgb camera can track markers for now.
            // Camera is used to obtain the projective Device (calibration)
            cam = new CameraNectar(cameraName);
            cam.setUseColor(true);
            cam.actAsColorCamera();
            
            cam.DEFAULT_REDIS_HOST = host;
            cam.DEFAULT_REDIS_PORT = Integer.parseInt(port);

            // Connect to camera
            cam.start();

            // Get all all markerboards... 
//            redis.get(host)
            Set<String> boardKeys = redis.smembers("markerboards");
            for (String key : boardKeys) {
//                System.out.println("Key: " + key);
                String name = key.split("markerboards:")[1];
//                MarkerBoard markerboard = new MarkerBoardSvg(
                JSONArray markersJson = JSONArray.parse(redis.get(key));
                MarkerList markers = MarkerList.createFromJSON(markersJson);

                MarkerBoardSvg board = new MarkerBoardSvg(name, markers);
                board.addTracker(null, cam.getPublicCamera());
                markerboards.add(board);
            }
            System.out.println("ProjectiveDevice: " + cam.getProjectiveDevice());
            cam.addObserver(new ImageObserver());
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(MultiPoseEstimator.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Waiting for something.");
    }

    static class ImageObserver implements Observer {

        @Override
        public void update(Observable o, Object o1) {
            log("", "New Image.");
            for (MarkerBoard board : markerboards) {
                board.updateLocation(cam, cam.getIplImage(), null);

                String name = board.getFileName();
                String outputKey = cam.getCameraDescription() + ":markerboards:" + name;

                PMatrix3D position = board.getPosition(cam);
                JSONObject matrix = new JSONObject();
                JSONArray poseJson = PMatrixToJSON(position);

                matrix.setJSONArray("matrix", poseJson);

                redis.set(outputKey, matrix.toString());
                redis.publish(outputKey, matrix.toString());

                log("Set/Publish to " + outputKey + ".", matrix.toString());
            }
            log("", "New Image - end.");
        }

    }
    
// <editor-fold defaultstate="collapsed" desc="Command line parsing">
    static private void parseCLI(String[] passedArgs) {

        options = new Options();
        addDefaultOptions(options);

        options.addRequiredOption("i", "input", true, "Camera input key .");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        // -u -i markers -cc data/calibration-AstraS-rgb.yaml -mc data/A4-default.svg -o pose
        try {
            cmd = parser.parse(options, passedArgs);
            parseDefaultOptions(cmd);

            if (cmd.hasOption("i")) {
                cameraName = cmd.getOptionValue("i");
            }

        } catch (ParseException ex) {
            die(ex.toString(), true);
        }
    }
    // </editor-fold>
}
