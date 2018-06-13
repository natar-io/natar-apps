package tech.lity.rea.nectar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.xml.sax.SAXException;
import processing.core.*;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.XML;
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

        Options options = new Options();
        options.addOption("f", "file", true, "filename.");
        options.addOption("p", "path", true, "Optionnal path.");
        options.addOption("m", "matrix", false, "Activate when the file is a matrix.");
        options.addOption("pd", "projective-device", false, "Activate when the file is projective device.");

        // Generic options
        options.addOption("v", "verbose", false, "Verbose activated.");
        options.addOption("s", "silent", false, "Silent activated.");
        options.addOption("o", "output", true, "Output key.");
        options.addOption("rp", "redisport", true, "Redis port, default is: " + REDIS_PORT);
        options.addOption("rh", "redishost", true, "Redis host, default is: " + REDIS_HOST);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        boolean isMatrix = false;
        boolean isProjectiveDevice = false;

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
            Logger.getLogger(ConfigurationLoader.class
                    .getName()).log(Level.SEVERE, null, ex);
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
                pdp = ProjectiveDeviceP.loadCameraDevice(path + "/" + fileName);
                redis.set(output, pdp.toJSON().toString());

                log(fileName + " loaded to " + output, pdp.toJSON().toString());

            } catch (Exception ex) {
                Logger.getLogger(ConfigurationLoader.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }

//        try {
//            Path currentRelativePath = Paths.get("");
//            String path = currentRelativePath.toAbsolutePath().toString();
//
//            PMatrix3D camProjExtrinsics = loadCalibration(path + "/data/camProjExtrinsics.xml");
//            camProjExtrinsics.print();
//
//            // CamProj Homoraphy
//            PMatrix3D camProjHomography = loadCalibration(path + "/data/camProjHomography.xml");
//            JSONObject cp = new JSONObject();
//            cp.setJSONArray("matrix", ProjectiveDeviceP.PMatrixToJSON(camProjHomography));
//            redis.set(defaultName + ":cam_proj_homograhy", cp.toString());
//
//            // COLOR
//            ProjectiveDeviceP pdp = ProjectiveDeviceP.loadCameraDevice(path + "/data/calibration-AstraS-rgb.yaml");
//            redis.set(OUTPUT_PREFIX + defaultHost + ":calibration:astra-s-rgb", pdp.toJSON().toString());
//            cameraDevice = pdp;
//
//            // DEPTH
//            pdp = ProjectiveDeviceP.loadCameraDevice(path + "/data/calibration-AstraS-depth.yaml");
//            redis.set(OUTPUT_PREFIX + defaultHost + ":calibration:astra-s-depth", pdp.toJSON().toString());
//
//            /// Extrinsics
//            PMatrix3D extrinsics = loadCalibration(path + "/data/camProjExtrinsics.xml");
//            JSONObject cp1 = new JSONObject();
//            cp1.setJSONArray("matrix", ProjectiveDeviceP.PMatrixToJSON(extrinsics));
//            redis.set(OUTPUT_PREFIX + defaultHost + ":calibration:astra-extrinsics", cp.toString());
//
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            Logger.getLogger(FileLoader.class.getName()).log(Level.SEVERE, null, ex);
//        }
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
            redis = new Jedis("127.0.0.1", 6379);
            if (redis == null) {
                throw new Exception("Cannot connect to server. ");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        // redis.auth("156;2Asatu:AUI?S2T51235AUEAIU");
    }

}
