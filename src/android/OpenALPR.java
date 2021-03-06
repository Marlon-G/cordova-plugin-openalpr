package org.apache.cordova.openalpr;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.openalpr.jni.Alpr;
import com.openalpr.jni.AlprException;
import com.openalpr.jni.AlprPlate;
import com.openalpr.jni.AlprPlateResult;
import com.openalpr.jni.AlprResults;
import com.openalpr.util.Utils;

import android.content.Context;
import android.util.Log;
import android.util.Base64;

import java.io.File;

public class OpenALPR extends CordovaPlugin {

    /**
     * Constructor.
     */
    public OpenALPR() {
    }

    /**
     * Execute function, will handle the 'scan' action.
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return boolean
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("scan")) {
            try {
                String imagePath = args.getString(0);
                JSONObject options = args.getJSONObject(1);
                this.scan(imagePath, options, callbackContext);
            } catch (JSONException e) {
                //returnError("Could not read arguments", callbackContext);
                callbackContext.error("Could not read arguments");
            }
            return true;
        }

        return true;
    }

    /**
     * Returns (via callback) array of license plates found based on the given imagePath
     *
     * @param imagePath       path to image
     * @param options         options
     * @param callbackContext callback to JavaScript
     */
    private void scan(String imagePath, JSONObject options, CallbackContext callbackContext) {

        //Declare response array.
        JSONArray response = new JSONArray();

        //Check if imagePath is a file path.
        if (imagePath.contains("file://")) {

            //Remove unnecessary prefixes.
            imagePath = imagePath.replace("file://", "");

            //Make sure a image exists for given path.
            if (imagePath == null || imagePath.length() == 0 || ! new File(imagePath).isFile()) {
                //Otherwise return an error.
                //returnError("No image found for given file path", callbackContext);
                callbackContext.error("No image found for given file path");
            }

            //Initialize Alpr object.
            Alpr alpr = initAlpr(options, callbackContext);
            AlprResults results = null;

            //Try to recognize plates.
            try {
                results = alpr.recognize(imagePath);
            } catch (AlprException e) {
                //Handle any errors.
                //returnError("Error during recognizing plates: " + e.getMessage(), callbackContext);
                callbackContext.error("Error during recognizing plates: " + e.getMessage());
            }

            // Make sure to call this to release memory
            alpr.unload();

            //Try and build the JSONArray.
            try {
                response = buildResponse(results, response);
            } catch (JSONException e) {
                //Properly handle any errors.
                //returnError("Error during building response: " + e.getMessage(), callbackContext);
                callbackContext.error("Error during building response: " + e.getMessage());
            }
        } else if (imagePath.contains("content://")) {
            //This imagePath format is not supported please convert it to a proper file path.
            //returnError("ImagePath format is not supported.", callbackContext);
            callbackContext.error("ImagePath format is not supported.");
        } else {
            //Else treat it as a base64 encoded string.
            //Decode string.
            byte[] decoded = Base64.decode(imagePath, Base64.DEFAULT);

            //Return error if decoded array is empty.
            if (decoded.length == 0) {
                //returnError("Could not decode Base64 string.", callbackContext);
                callbackContext.error("Could not decode Base64 string.");
            }

            //Initialize new Alpr object.
            Alpr alpr = initAlpr(options, callbackContext);
            AlprResults results = null;
                callbackContext.error("Inicializacion alcanzada");

            //Try to recognize plates.
            try {
                results = alpr.recognize(decoded);
            } catch (AlprException e) {
                //Properly handle any errors.
                //returnError("Error during recognizing plates: " + e.getMessage(), callbackContext);
                callbackContext.error("Error during recognizing plates: " + e.getMessage());
            }

            // Make sure to call this to release memory
            alpr.unload();

            //Try to build a response JSONArray.
            try {
                response = buildResponse(results, response);
            } catch (JSONException e) {
                //Properly handle any errors that may occur.
                //returnError("Error during building response: " + e.getMessage(), callbackContext);
                callbackContext.error("Error during building response: " + e.getMessage());
            }
        }

        //Create Cordova result and send it back with the callback.
        PluginResult cordovaResult = new PluginResult(PluginResult.Status.OK, response);
        callbackContext.sendPluginResult(cordovaResult);
    }

    /**
     * Initialize new Alpr object.
     *
     * @param options         Options to use when intializing new Alpr object. Expects country and amount.
     * @param callbackContext Callback to JS.
     * @return new Alpr object.
     */
    private Alpr initAlpr(JSONObject options, CallbackContext callbackContext) {
        String country = "";
        Integer amount = 0;

        //Try to read options object.
        try {
            country = options.getString("country");
            amount = options.getInt("amount");
        } catch (JSONException e) {
            //returnError("Error while reading options: " + e.getMessage(), callbackContext);
            callbackContext.error("Error while reading options: " + e.getMessage());
        }

        // Copy assets/runtime_data to accessible Android data dir.
        Context context = this.cordova.getActivity().getApplicationContext();
        String androidDataDir = context.getApplicationInfo().dataDir;
        Utils.copyAssetFolder(context.getAssets(), "runtime_data",
                androidDataDir + File.separatorChar + "runtime_data");

        String runtime_dir = androidDataDir + File.separatorChar + "runtime_data";
        String conf_file = runtime_dir + File.separatorChar + "openalpr.conf";

        Alpr alpr = new Alpr(country, conf_file, runtime_dir); //Make new ALPR object with country EU and the config files from assets.
                callbackContext.error("Inicializacion paso 1");
        alpr.setTopN(amount);

        return alpr;
    }

    /**
     * Function to build a proper JSON response array.
     *
     * @param results Result to be sent.
     * @param array   Array to be build into a proper response.
     * @return JSONArray
     * @throws JSONException Will throw a JSONException when JSONArray can not be filled.
     */
    private JSONArray buildResponse(AlprResults results, JSONArray array) throws JSONException {
        for (AlprPlateResult result : results.getPlates()) {
            for (AlprPlate plate : result.getTopNPlates()) {
                JSONObject obj = new JSONObject();
                obj.put("number", plate.getCharacters());
                obj.put("confidence", plate.getOverallConfidence());
                array.put(obj);
            }
        }
        return array;
    }

    /**
     * Function to build a proper error response.
     *
     * @param message         Message for the error.
     * @param callbackContext Callback to JS (To send the error to).
     */
    private void returnError(String message, CallbackContext callbackContext) {
        Log.v("OpenALPR", message);
        JSONObject error = new JSONObject();

        try {
            error.put("code", 0);
            error.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        callbackContext.error(error);
    }
}
