package test;

import static test.GlUtils.getCurrentTexture;
import static test.GlUtils.getGl2;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLPbuffer;
import javax.media.opengl.GLRunnable;

public class PbufferScenePostprocessor extends ScenePostprocessor {
    private boolean renderToTexture;

    private GLPbuffer pbuffer;

    public void setRenderToTexture(boolean renderToTexture) {
	this.renderToTexture = renderToTexture;
    }

    @Override
    protected boolean isCreateRenderedSceneTexture() {
	return !renderToTexture;
    }

    @Override
    protected Integer initSceneRendering(GLAutoDrawable drawable) {
	GLCapabilities capabilities = (GLCapabilities) drawable
		.getChosenGLCapabilities().cloneMutable();

	capabilities.setPbufferFloatingPointBuffers(isHdr());
	capabilities.setPbufferRenderToTexture(renderToTexture);
	capabilities.setPbufferRenderToTextureRectangle(renderToTexture
		&& isTextureRectangle());

	if (isHdr()) {
	    capabilities.setRedBits(16);
	    capabilities.setGreenBits(16);
	    capabilities.setBlueBits(16);
	}

	pbuffer = GLDrawableFactory.getFactory(drawable.getGLProfile())
		.createGLPbuffer(null, capabilities, null,
			Math.max(drawable.getWidth(), 1),
			Math.max(drawable.getHeight(), 1),
			drawable.getContext());

	pbuffer.addGLEventListener(getSceneRenderer());

	if (renderToTexture) {
	    pbuffer.getContext().makeCurrent();

	    pbuffer.bindTexture();
	    int renderedSceneTexture = getCurrentTexture(getGl2(pbuffer),
		    getRenderedSceneTextureTarget());

	    drawable.getContext().makeCurrent();

	    return renderedSceneTexture;
	} else {
	    return null;
	}
    }

    @Override
    protected void renderSceneTexture(GLAutoDrawable drawable) {
	if (renderToTexture) {
	    pbuffer.display();
	} else {
	    pbuffer.invoke(true, new GLRunnable() {
		@Override
		public void run(GLAutoDrawable drawable) {
		    GL2 gl = getGl2(drawable);

		    int textureTarget = getRenderedSceneTextureTarget();

		    int currentTexture = getCurrentTexture(
			    gl,
			    isTextureRectangle() ? GL2.GL_TEXTURE_BINDING_RECTANGLE
				    : GL2.GL_TEXTURE_BINDING_2D);

		    gl.glBindTexture(textureTarget, getRenderedSceneTexture());
		    gl.glCopyTexSubImage2D(textureTarget, 0, 0, 0, 0, 0,
			    drawable.getWidth(), drawable.getHeight());

		    gl.glFlush();

		    gl.glBindTexture(textureTarget, currentTexture);
		}
	    });
	}
    }

    @Override
    protected void shutdownSceneRendering(GLAutoDrawable drawable) {
	// sometimes error occurs in JOGL 2.0b11 on next pbuffer creation when
	// current context was not released explicitly before destroying pbuffer
	GLContext context = drawable.getContext();
	context.release();

	if (renderToTexture) {
	    pbuffer.releaseTexture();
	}

	pbuffer.destroy();

	context.makeCurrent();

	pbuffer.removeGLEventListener(getSceneRenderer());

	pbuffer = null;
    }
}
