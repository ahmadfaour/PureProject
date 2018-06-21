package org.tensorflow.demo;

import android.app.Application;

/**
 *  The float array : styleVals is to save the rate of each style
 *  we desire to activate on the image we select
 */
public class GlobalVariables extends Application{
    private static final int NUM_STYLES = 26;
    public static final float[] styleVals = new float[NUM_STYLES];

    /**
     * intialization the styleVals array.
     */
    public static void initialStyleVals(){
        for (int i = 0; i < styleVals.length; i++) {
            styleVals[i] = 0;
        }
        styleVals[0]=1.0f;
    }

    /**
     *
     * @return the styleVals array
     */
    public static float[] getStyleVals() {
        return styleVals;
    }

    /**
     *
     * @return number of styles.
     */
    public static int getNumStyles(){
        return NUM_STYLES;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        initialStyleVals();
    }

}
