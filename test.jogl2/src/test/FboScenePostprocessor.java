package test;

import static test.GlUtils.getGl2;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLContext;

public class FboScenePostprocessor extends ScenePostprocessor {
    private GLContext context;

    private int framebuffer;

    private int depthRenderbuffer;

    @Override
    protected boolean isCreateRenderedSceneTexture() {
	return true;
    }

    @Override
    protected Integer initSceneRendering(GLAutoDrawable drawable) {
	GLContext parentContext = drawable.getContext();
	context = drawable.createContext(parentContext);

	setContextCurrent(drawable);

	getSceneRenderer().init(drawable);

	GL2 gl = getGl2(drawable);

	int[] framebuffers = new int[1];
	gl.glGenFramebuffers(1, framebuffers, 0);
	framebuffer = framebuffers[0];

	gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, framebuffer);

	gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0,
		getRenderedSceneTextureTarget(), getRenderedSceneTexture(), 0);

	int[] renderbuffers = new int[1];
	gl.glGenRenderbuffers(1, renderbuffers, 0);
	depthRenderbuffer = renderbuffers[0];

	gl.glBindRenderbuffer(GL2.GL_RENDERBUFFER, depthRenderbuffer);

	gl.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT,
		Math.max(drawable.getWidth(), 1), Math.max(
			drawable.getHeight(), 1));

	gl
		.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER,
			GL2.GL_DEPTH_ATTACHMENT, GL2.GL_RENDERBUFFER,
			depthRenderbuffer);

	setContextCurrent(drawable, parentContext);

	return null;
    }

    @Override
    protected void renderSceneTexture(GLAutoDrawable drawable) {
	GLContext parentContext = setContextCurrent(drawable);

	getSceneRenderer().display(drawable);

	setContextCurrent(drawable, parentContext);
    }

    @Override
    protected void shutdownSceneRendering(GLAutoDrawable drawable) {
	GLContext parentContext = setContextCurrent(drawable);

	getSceneRenderer().dispose(drawable);

	GL2 gl = getGl2(drawable);

	gl.glDeleteRenderbuffers(1, new int[] { depthRenderbuffer }, 0);

	gl.glDeleteFramebuffers(1, new int[] { framebuffer }, 0);

	// in JOGL 2.0b11 if secondary context (used to render to framebuffer)
	// destroyed when main context is current it leads to problem on next
	// secondary context creation (it is created but some operations on that
	// failed)
	context.destroy();

	setContextCurrent(drawable, parentContext);

	context = null;
    }

    private GLContext setContextCurrent(GLAutoDrawable drawable) {
	return setContextCurrent(drawable, context);
    }

    private GLContext setContextCurrent(GLAutoDrawable drawable,
	    GLContext context) {
	GLContext currentContext = drawable.getContext();

	drawable.setContext(context);
	context.makeCurrent();

	return currentContext;
    }
}
