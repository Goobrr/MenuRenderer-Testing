package mrend;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.Pixmap.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.util.*;
import mindustry.graphics.*;

// I dont wanna bother with reflection

// but this is equally as painful

public class CustomBloom extends Bloom{
    public int blurPasses = 1;
    public boolean blending = false;

    private Shader thresholdShader, bloomShader, blurShader;
    private FrameBuffer buffer, pingPong1, pingPong2;

    private float bloomIntensity, originalIntensity, threshold;
    private boolean capturing = false;
    private float r, g, b, a;

    /** Rebinds the context. Necessary on Android/IOS. TODO or is it? */
    public void resume(){
        bloomShader.bind();
        bloomShader.setUniformi("u_texture0", 0);
        bloomShader.setUniformi("u_texture1", 1);

        setSize(pingPong1.getWidth(), pingPong1.getHeight());
        setThresholdc(threshold);
        setBloomIntensity(bloomIntensity);
        setOriginalIntensity(originalIntensity);
    }

    /** Creates a bloom instance with no blending, no depth and 1/4 the screen size. */
    public CustomBloom(){
        initc(Core.graphics.getWidth() / 4, Core.graphics.getHeight() / 4, false, false);
    }

    public CustomBloom(boolean useBlending){
        initc(Core.graphics.getWidth() / 4, Core.graphics.getHeight() / 4, false, useBlending);
    }

    /**
     * Initialize bloom class that capsulate original scene capturate,
     * thresholding, gaussian blurring and blending.
     *
     * @param hasDepth do rendering need depth buffer
     * @param useBlending does fbo need alpha channel and is blending enabled when final image is rendered.
     * This allows combining background graphics and only doing blooming on certain objects.
     */
    public CustomBloom(int width, int height, boolean hasDepth, boolean useBlending){
        initc(width, height, hasDepth, useBlending);
    }

    public void resize(int width, int height){
        resize(width, height, 4);
    }

    public void resize(int width, int height, int scaling){
        boolean changed = (pingPong1.getWidth() != width / scaling || pingPong1.getHeight() != height / scaling);

        if(changed){
            pingPong1.resize(width / scaling, height / scaling);
            pingPong2.resize(width / scaling, height / scaling);
            buffer.resize(width, height);
            setSize(width / scaling, height / scaling);
        }
    }

    private void initc(int width, int height, boolean hasDepth, boolean useBlending){
        blending = useBlending;
        //rgba8888 is generally well-supported, rgb888 may be slower
        Format format = Format.rgba8888;

        buffer = new FrameBuffer(format, Core.graphics.getWidth(), Core.graphics.getHeight(), hasDepth);
        pingPong1 = new FrameBuffer(format, width, height, false);
        pingPong2 = new FrameBuffer(format, width, height, false);

        final String alpha = useBlending ? "alpha_" : "";

        bloomShader = makeShader("screenspace", alpha + "bloom");
        thresholdShader = makeShader("screenspace", alpha + "threshold");

        blurShader = makeShader("blurspace", alpha + "gaussian");

        setSize(width, height);
        setBloomIntensity(1.2f);
        setOriginalIntensity(0.5f);
        setThresholdc(0.5f);

        bloomShader.bind();
        bloomShader.setUniformi("u_texture0", 0);
        bloomShader.setUniformi("u_texture1", 1);
    }

    /** Set clearing color for capturing buffer */
    public void setClearColor(float r, float g, float b, float a){
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    /** Call this before rendering scene. */
    public void capture(){
        if(!capturing){
            capturing = true;
            buffer.begin();
            Gl.clearColor(r, g, b, a);
            Gl.clear(Gl.colorBufferBit | Gl.depthBufferBit);
        }
    }

    /** Pause capturing to fbo. */
    public void capturePause(){
        if(capturing){
            capturing = false;
            buffer.end();
        }
    }

    /** Start capturing again after pause, no clearing is done to framebuffer */
    public void captureContinue(){
        if(!capturing){
            capturing = true;
            buffer.begin();
        }
    }

    /** Renders the bloomed scene. */
    public void render(){
        if(capturing){
            capturing = false;
            buffer.end();
        }

        Gl.disable(Gl.blend);
        Gl.disable(Gl.depthTest);
        Gl.depthMask(false);

        gaussianBlur();

        if(blending){
            Gl.enable(Gl.blend);
            Gl.blendFunc(Gl.srcAlpha, Gl.one);
        }

        pingPong1.getTexture().bind(1);
        buffer.blit(bloomShader);
    }

    private void gaussianBlur(){
        //cut bright areas of the picture and blit to smaller fbo

        pingPong1.begin();
        buffer.blit(thresholdShader);
        pingPong1.end();

        for(int i = 0; i < blurPasses; i++){

            // horizontal
            pingPong2.begin();
            blurShader.bind();
            blurShader.setUniformf("dir", 1f, 0f);
            pingPong1.blit(blurShader);
            pingPong2.end();

            // vertical
            pingPong1.begin();
            blurShader.bind();
            blurShader.setUniformf("dir", 0f, 1f);
            pingPong2.blit(blurShader);
            pingPong1.end();
        }
    }

    /**
     * set intensity for bloom. higher mean more brightening for spots that are
     * over threshold
     *
     * @param intensity multiplier for blurred texture in combining phase. must be
     * positive.
     */
    public void setBloomIntensity(float intensity){
        bloomIntensity = intensity;
        bloomShader.bind();
        bloomShader.setUniformf("BloomIntensity", intensity);
    }

    /**
     * set intensity for original scene. under 1 mean darkening and over 1 means
     * lightening
     *
     * @param intensity multiplier for captured texture in combining phase. must be
     * positive.
     */
    public void setOriginalIntensity(float intensity){
        originalIntensity = intensity;
        bloomShader.bind();
        bloomShader.setUniformf("OriginalIntensity", intensity);
    }

    /**
     * threshold for bright parts. everything under threshold is clamped to 0
     *
     * @param threshold must be in range 0..1
     */
    public void setThresholdc(float threshold){
        this.threshold = threshold;
        thresholdShader.bind();
        thresholdShader.setUniformf("threshold", threshold, 1f / (1 - threshold));
    }

    private void setSize(int width, int height){
        blurShader.bind();
        blurShader.setUniformf("size", width, height);
    }

    /** Disposes all resources. */
    public void dispose(){
        try{
            buffer.dispose();
            pingPong1.dispose();
            pingPong2.dispose();

            blurShader.dispose();
            bloomShader.dispose();
            thresholdShader.dispose();
        }catch(Throwable ignored){

        }
    }

    private static Shader makeShader(String vertexName, String fragmentName){
        return new Shader(Core.files.internal("bloomshaders/" + vertexName + ".vert"), Core.files.internal("bloomshaders/" + fragmentName + ".frag"));
    }

}
