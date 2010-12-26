package test;

import static test.GlUtils.createProgram;
import static test.GlUtils.createShader;
import static test.GlUtils.createTexture;
import static test.GlUtils.deleteTextures;
import static test.GlUtils.getGl2;
import static test.GlUtils.getUniformLocation;
import static test.GlUtils.useDebugGl;

import java.io.IOException;
import java.io.InputStream;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLDrawable;
import javax.media.opengl.GLEventListener;

public abstract class ScenePostprocessor implements GLEventListener {
    private GLEventListener sceneRenderer;

    private boolean debugGl;

    private boolean textureRectangle = true;

    private boolean hdr;

    private int postprocessorProgram;

    private int postprocessorPixelShader;

    private int renderedSceneTextureTarget;

    private Integer renderedSceneTexture;

    private String postprocessor;

    private Integer sceneTextureUniformLocation;

    private Integer sceneTextureSizeUniformLocation;

    private Integer sceneTextureMaxCoordUniformLocation;

    public void setSceneRenderer(GLEventListener sceneRenderer) {
	this.sceneRenderer = sceneRenderer;
    }

    public void setDebugGl(boolean debugGl) {
	this.debugGl = debugGl;
    }

    public void setTextureRectangle(boolean textureRectangle) {
	this.textureRectangle = textureRectangle;
    }

    public void setHdr(boolean hdr) {
	this.hdr = hdr;
    }

    public void setPostprocessor(String postprocessor) {
	this.postprocessor = postprocessor;
    }

    @Override
    public void init(GLAutoDrawable drawable) {
	GL2 gl = useDebugGl(drawable, debugGl);

	gl.glClearColor(0, 0, 0, 1);
	gl.glClear(GL2.GL_COLOR_BUFFER_BIT);

	gl.glMatrixMode(GL2.GL_PROJECTION);
	gl.glLoadIdentity();

	gl.glMatrixMode(GL2.GL_MODELVIEW);
	gl.glLoadIdentity();

	renderedSceneTextureTarget = textureRectangle ? GL2.GL_TEXTURE_RECTANGLE
		: GL2.GL_TEXTURE_2D;

	InputStream shaderTemplateSource = JoglTest.class
		.getResourceAsStream("postprocessors/" + postprocessor
			+ ".glsl.template");
	if (shaderTemplateSource == null) {
	    throw new IllegalArgumentException("cannot find postprocessor: "
		    + postprocessor);
	}

	try {
	    postprocessorPixelShader = createShader(gl, shaderTemplateSource,
		    textureRectangle);
	} catch (IOException exception) {
	    throw new IllegalArgumentException(exception);
	}

	postprocessorProgram = createProgram(gl, postprocessorPixelShader);

	sceneTextureUniformLocation = getUniformLocation(gl,
		postprocessorProgram, "sceneTexture");
	sceneTextureSizeUniformLocation = getUniformLocation(gl,
		postprocessorProgram, "sceneTextureSize");
	sceneTextureMaxCoordUniformLocation = getUniformLocation(gl,
		postprocessorProgram, "sceneTextureMaxCoord");

	gl.glUseProgram(postprocessorProgram);

	doInitSceneRendering(drawable);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width,
	    int height) {
	doShutdownSceneRendering(drawable);
	doInitSceneRendering(drawable);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
	renderSceneTexture(drawable);

	GL2 gl = getGl2(drawable);

	gl.glClear(GL2.GL_DEPTH_BUFFER_BIT);

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
	doShutdownSceneRendering(drawable);

	GL2 gl = getGl2(drawable);
	gl.glDeleteShader(postprocessorPixelShader);
	gl.glDeleteProgram(postprocessorProgram);
    }

    protected GLEventListener getSceneRenderer() {
	return sceneRenderer;
    }

    protected boolean isTextureRectangle() {
	return textureRectangle;
    }

    protected boolean isHdr() {
	return hdr;
    }

    protected int getRenderedSceneTextureTarget() {
	return renderedSceneTextureTarget;
    }

    protected Integer getRenderedSceneTexture() {
	return renderedSceneTexture;
    }

    protected abstract boolean isCreateRenderedSceneTexture();

    protected abstract Integer initSceneRendering(GLAutoDrawable drawable);

    protected abstract void renderSceneTexture(GLAutoDrawable drawable);

    protected abstract void shutdownSceneRendering(GLAutoDrawable drawable);

    private void doInitSceneRendering(GLAutoDrawable drawable) {
	GL2 gl = getGl2(drawable);

	int width = Math.max(drawable.getWidth(), 1);
	int height = Math.max(drawable.getHeight(), 1);

	if (isCreateRenderedSceneTexture()) {
	    renderedSceneTexture = createTexture(gl, renderedSceneTextureTarget);
	    gl.glBindTexture(renderedSceneTextureTarget, renderedSceneTexture);
	    gl.glTexImage2D(renderedSceneTextureTarget, 0, hdr ? GL2.GL_RGBA16F
		    : GL2.GL_RGBA, width, height, 0, GL2.GL_RGBA,
		    hdr ? GL2.GL_FLOAT : GL2.GL_UNSIGNED_BYTE, null);
	} else {
	    renderedSceneTexture = null;
	}

	Integer initSceneRenderingResult = initSceneRendering(drawable);

	if (sceneTextureUniformLocation != null) {
	    gl.glActiveTexture(GL2.GL_TEXTURE0);
	    gl.glUniform1i(sceneTextureUniformLocation, 0);
	    gl.glBindTexture(renderedSceneTextureTarget,
		    renderedSceneTexture != null ? renderedSceneTexture
			    : initSceneRenderingResult);
	}
	if (sceneTextureSizeUniformLocation != null) {
	    gl.glUniform2f(sceneTextureSizeUniformLocation, width, height);
	}
	if (sceneTextureMaxCoordUniformLocation != null) {
	    gl
		    .glUniform2f(sceneTextureMaxCoordUniformLocation,
			    textureRectangle ? width : 1,
			    textureRectangle ? height : 1);
	}
    }

    private void doShutdownSceneRendering(GLAutoDrawable drawable) {
	shutdownSceneRendering(drawable);

	if (renderedSceneTexture != null) {
	    deleteTextures(getGl2(drawable), renderedSceneTexture);
	}
    }

    private void drawVertex(GL2 gl, int x, int y) {
	int textureX = (x + 1) / 2;
	int textureY = (y + 1) / 2;
	if (textureRectangle) {
	    GLDrawable drawable = gl.getContext().getGLDrawable();
	    textureX *= drawable.getWidth() - 1;
	    textureY *= drawable.getHeight() - 1;
	}
	gl.glTexCoord2f(textureX, textureY);

	gl.glVertex2f(x, y);
    }
}
