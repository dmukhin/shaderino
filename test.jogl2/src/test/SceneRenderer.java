package test;

import static test.GlUtils.createProgram;
import static test.GlUtils.createShader;
import static test.GlUtils.createTexture;
import static test.GlUtils.deleteTextures;
import static test.GlUtils.getGl2;
import static test.GlUtils.getUniformLocation;
import static test.GlUtils.loadBGRTexture;
import static test.GlUtils.useDebugGl;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;

public class SceneRenderer implements GLEventListener {
    private boolean debugGl;

    private String effect;

    private String image;

    private boolean textureRectangle = true;

    private volatile float rotationAngle;

    private Map<String, Object> effectParameters = new HashMap<String, Object>();

    private Integer windowPositionUniformLocation;

    private Integer windowSizeUniformLocation;

    private Integer rotationAngleUniformLocation;

    private int effectProgram;

    private int effectPixelShader;

    private Integer texture;

    private Integer textureTarget;

    private Dimension textureSize;

    public void setDebugGl(boolean debugGl) {
	this.debugGl = debugGl;
    }

    public void setEffect(String effect) {
	this.effect = effect;
    }

    public void setImage(String image) {
	this.image = image;
    }

    public void setTextureRectangle(boolean textureRectangle) {
	this.textureRectangle = textureRectangle;
    }

    public void setRotationAngle(float rotationAngle) {
	this.rotationAngle = rotationAngle;
    }

    public void setEffectParameters(Map<String, Object> effectParameters) {
	this.effectParameters = effectParameters;
    }

    @Override
    public void init(GLAutoDrawable drawable) {
	final GL2 gl = useDebugGl(drawable, debugGl);

	gl.glClearColor(0, 0, 0, 1);

	gl.glMatrixMode(GL2.GL_PROJECTION);
	gl.glLoadIdentity();

	gl.glMatrixMode(GL2.GL_MODELVIEW);

	InputStream shaderTemplateSource = JoglTest.class
		.getResourceAsStream("effects/" + effect + ".glsl.template");
	if (shaderTemplateSource == null) {
	    throw new IllegalArgumentException("cannot find effect: " + effect);
	}

	try {
	    effectPixelShader = createShader(gl, shaderTemplateSource,
		    textureRectangle, (debugGl ? "effect " + effect
			    + " shader compilation log: " : null));
	} catch (IOException exception) {
	    throw new RuntimeException(exception);
	}

	effectProgram = createProgram(gl, effectParameters,
		(debugGl ? "effect " + effect + " shader program linking log: "
			: null), effectPixelShader);

	windowPositionUniformLocation = getUniformLocation(gl, effectProgram,
		"windowPosition");
	windowSizeUniformLocation = getUniformLocation(gl, effectProgram,
		"windowSize");
	rotationAngleUniformLocation = getUniformLocation(gl, effectProgram,
		"rotationAngle");

	gl.glUseProgram(effectProgram);

	Integer textureUniformLocation = getUniformLocation(gl, effectProgram,
		"texture");
	if (textureUniformLocation != null) {
	    InputStream textureSource = JoglTest.class
		    .getResourceAsStream("images/" + image + ".png");
	    if (textureSource == null) {
		throw new IllegalArgumentException("cannot find image: "
			+ image);
	    }

	    textureTarget = textureRectangle ? GL2.GL_TEXTURE_RECTANGLE
		    : GL2.GL_TEXTURE_2D;

	    texture = createTexture(gl, textureTarget);
	    try {
		textureSize = loadBGRTexture(gl, textureTarget, textureSource);
	    } catch (IOException exception) {
		throw new RuntimeException(exception);
	    }

	    gl.glActiveTexture(GL2.GL_TEXTURE0);
	    gl.glBindTexture(textureTarget, texture);
	    gl.glUniform1i(textureUniformLocation, 0);

	    Integer textureSizeUniformLocation = getUniformLocation(gl,
		    effectProgram, "textureSize");
	    if (textureSizeUniformLocation != null) {
		gl.glUniform2f(textureSizeUniformLocation, (float) textureSize
			.getWidth(), (float) textureSize.getHeight());
	    }

	    Integer textureMaxCoordUniformLocation = getUniformLocation(gl,
		    effectProgram, "textureMaxCoord");
	    if (textureMaxCoordUniformLocation != null) {
		gl.glUniform2f(textureMaxCoordUniformLocation,
			textureRectangle ? (float) textureSize.getWidth() : 1,
			textureRectangle ? (float) textureSize.getHeight() : 1);
	    }

	}

	setEffectShaderViewportUniforms(gl, 0, 0, drawable.getWidth(), drawable
		.getHeight());
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int width,
	    int height) {
	setEffectShaderViewportUniforms(getGl2(drawable), x, y, width, height);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
	GL2 gl = getGl2(drawable);

	gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

	gl.glLoadIdentity();
	gl.glRotatef(rotationAngle, 0, 0, 1);

	if (rotationAngleUniformLocation != null) {
	    gl.glUniform1f(rotationAngleUniformLocation, rotationAngle);
	}

	gl.glBegin(GL2.GL_POLYGON);
	drawVertex(gl, -1, -1);
	drawVertex(gl, 1, -1);
	drawVertex(gl, 1, 1);
	drawVertex(gl, -1, 1);
	gl.glEnd();

	gl.glFlush();
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
	GL2 gl = getGl2(drawable);

	gl.glDeleteShader(effectPixelShader);
	gl.glDeleteProgram(effectProgram);

	if (texture != null) {
	    deleteTextures(gl, texture);
	}
    }

    private void setEffectShaderViewportUniforms(GL2 gl, int x, int y,
	    int width, int height) {
	if (windowPositionUniformLocation != null) {
	    gl.glUniform2f(windowPositionUniformLocation, x, y);
	}
	if (windowSizeUniformLocation != null) {
	    gl.glUniform2f(windowSizeUniformLocation, width, height);
	}
    }

    private void drawVertex(GL2 gl, int x, int y) {
	if (texture != null) {
	    int textureX = (x + 1) / 2;
	    int textureY = (y + 1) / 2;
	    if (textureRectangle) {
		textureX *= textureSize.getWidth() - 1;
		textureY *= textureSize.getHeight() - 1;
	    }
	    gl.glTexCoord2f(textureX, textureY);
	}

	gl.glVertex2f(0.7f * x, 0.7f * y);
    }
}
