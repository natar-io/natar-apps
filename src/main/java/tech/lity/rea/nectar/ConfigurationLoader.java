package tech.lity.rea.nectar;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import processing.core.*;
import processing.data.JSONObject;
import redis.clients.jedis.Jedis;
import tech.lity.rea.nectar.calibration.files.HomographyCalibration;

/**
 *
 * @author Jeremy Laviole, <laviole@rea.lity.tech>
 */
@SuppressWarnings("serial")
public class ConfigurationLoader {

    static Jedis redis;

    public static final String REDIS_PORT = "6379";
    public static final String REDIS_HOST = "localhost";

    static String pathName = "";
    static String fileName = "";
    static String output = "config";
    static String host = REDIS_HOST;
    static String port = REDIS_PORT;

    static Options options;

    public static void die(String why, boolean usage) {
        if (usage) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("PoseEstimator", options);
        }
        System.out.println(why);
        System.exit(-1);
    }

    public static void die(String why) {
        System.out.println(why);
        System.exit(-1);
    }

    static boolean isVerbose = false;
    static boolean isSilent = false;

    public static void log(String normal, String verbose) {

        if (isSilent) {
            return;
        }
        System.out.println(normal);
        if (isVerbose) {
            System.out.println(verbose);
        }
    }

    static public void main(String[] passedArgs) {

        options = new Options();
        options.addRequiredOption("f", "file", true, "filename.");
        options.addOption("p", "path", true, "Optionnal path.");
        options.addOption("m", "matrix", false, "Activate when the file is a matrix.");
        options.addOption("pd", "projective-device", false, "Activate when the file is projective device.");
        options.addOption("pr", "projector", false, "Load a projector configuration, instead of camera.");

        // Generic options
        options.addOption("v", "verbose", false, "Verbose activated.");
        options.addOption("s", "silent", false, "Silent activated.");
        options.addRequiredOption("o", "output", true, "Output key.");
        options.addOption("rp", "redisport", true, "Redis port, default is: " + REDIS_PORT);
        options.addOption("rh", "redishost", true, "Redis host, default is: " + REDIS_HOST);

        options.addOption("h", "help", false, "print this help.");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        boolean isMatrix = false;
        boolean isProjectiveDevice = false;
        boolean isProjector = false;

        try {
            cmd = parser.parse(options, passedArgs);

            if (cmd.hasOption("f")) {
                fileName = cmd.getOptionValue("f");
            } else {
                die("Please input a filename with -f of --file option.");
            }
            if (cmd.hasOption("o")) {
                output = cmd.getOptionValue("o");
            } else {
                die("Please set an output key with -o or --output ");
            }

            if (cmd.hasOption("h")) {
                die("", true);
            }

            if (cmd.hasOption("p")) {
                pathName = cmd.getOptionValue("p");
            }
            if (cmd.hasOption("m")) {
                isMatrix = true;
            }
            if (cmd.hasOption("v")) {
                isVerbose = true;
            }
            if (cmd.hasOption("s")) {
                isSilent = true;
            }
            if (cmd.hasOption("pd")) {
                isProjectiveDevice = true;
            }
            if (cmd.hasOption("pr")) {
                isProjector = true;
            }
            if (cmd.hasOption("rh")) {
                host = cmd.getOptionValue("rh");
            }
            if (cmd.hasOption("rp")) {
                port = cmd.getOptionValue("rp");
            }

            if (!(isMatrix || isProjectiveDevice)) {
                die("Please specifiy the type of file: matrix, or projective device.");

            }

        } catch (ParseException ex) {
            die(ex.toString(), true);
        }

        connectRedis();

        Path currentRelativePath = Paths.get(pathName);
        String path = currentRelativePath.toAbsolutePath().toString();

        if (isMatrix) {
            // CamProj Homoraphy
            PMatrix3D mat = loadCalibration(path + "/" + fileName);
            if (mat == null) {
                die("Cannot read the matrix from: " + fileName);
            }
            JSONObject cp = new JSONObject();
            cp.setJSONArray(output, ProjectiveDeviceP.PMatrixToJSON(mat));
            redis.set(output, cp.toString());

            log(fileName + " loaded to " + output, cp.toString());
        }

        if (isProjectiveDevice) {
            ProjectiveDeviceP pdp;
            try {
                if (isProjector) {
                    pdp = ProjectiveDeviceP.loadProjectorDevice(path + "/" + fileName);
                } else {
                    pdp = ProjectiveDeviceP.loadCameraDevice(path + "/" + fileName);
                }
                redis.set(output, pdp.toJSON().toString());

                log(fileName + " loaded to " + output, pdp.toJSON().toString());

            } catch (Exception ex) {
                Logger.getLogger(ConfigurationLoader.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static PMatrix3D loadCalibration(String fileName) {
//        File f = new File(sketchPath() + "/" + fileName);
        File f = new File(fileName);
        if (f.exists()) {
            try {
                //            return HomographyCalibration.getMatFrom(this, sketchPath() + "/" + fileName);
                return HomographyCalibration.getMatFrom(fileName);

            } catch (Exception ex) {
                Logger.getLogger(ConfigurationLoader.class
                        .getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        } else {
            return null;
        }
    }

    private static void connectRedis() {
        try {
            redis = new Jedis(host, Integer.parseInt(port));
            if (redis == null) {
                throw new Exception("Cannot connect to server. ");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

}
