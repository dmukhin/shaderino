package dmukhin.jogl2_example;

import static dmukhin.jogl2_example.GlUtils.getGl2;
import static dmukhin.jogl2_example.GlUtils.useDebugGl;

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

        GL2 gl = useDebugGl(drawable, isDebugGl());

        int width = drawable.getWidth();
        int height = drawable.getHeight();

        gl.glViewport(0, 0, width, height);

        int[] framebuffers = new int[1];
        gl.glGenFramebuffers(1, framebuffers, 0);
        framebuffer = framebuffers[0];

        bindFramebuffer(drawable);

        gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0,
                getRenderedSceneTextureTarget(), getRenderedSceneTexture(), 0);

        int[] renderbuffers = new int[1];
        gl.glGenRenderbuffers(1, renderbuffers, 0);
        depthRenderbuffer = renderbuffers[0];

        gl.glBindRenderbuffer(GL2.GL_RENDERBUFFER, depthRenderbuffer);

        gl.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT,
                Math.max(width, 1), Math.max(height, 1));

        gl.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER,
                GL2.GL_DEPTH_ATTACHMENT, GL2.GL_RENDERBUFFER, depthRenderbuffer);

        getSceneRenderer().init(drawable);

        unbindFramebuffer(drawable);
        setContextCurrent(drawable, parentContext);

        return null;
    }

    @Override
    protected void renderSceneTexture(GLAutoDrawable drawable) {
        GLContext parentContext = setContextCurrent(drawable);
        bindFramebuffer(drawable);

        getSceneRenderer().display(drawable);

        unbindFramebuffer(drawable);
        setContextCurrent(drawable, parentContext);
    }

    @Override
    protected void shutdownSceneRendering(GLAutoDrawable drawable) {
        GLContext parentContext = setContextCurrent(drawable);
        bindFramebuffer(drawable);

        getSceneRenderer().dispose(drawable);

        unbindFramebuffer(drawable);

        GL2 gl = getGl2(drawable);

        gl.glDeleteRenderbuffers(1, new int[] { depthRenderbuffer }, 0);

        gl.glDeleteFramebuffers(1, new int[] { framebuffer }, 0);

        // in JOGL 2.0b11 if secondary context (used to render to framebuffer)
        // destroyed when main context is current, it leads to problem on next
        // operations
        context.release();
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

        drawable.setContext(context, false);
        context.makeCurrent();

        return currentContext;
    }

    private void bindFramebuffer(GLAutoDrawable drawable) {
        getGl2(drawable).glBindFramebuffer(GL2.GL_FRAMEBUFFER, framebuffer);
    }

    // if framebuffer is not unbound in renderSceneTexture after processed
    // scene rendered, it leads to corruption (appeared on Intel GMA 4500 with
    // multi-monitor configuration, usually on secondary monitor)
    private void unbindFramebuffer(GLAutoDrawable drawable) {
        getGl2(drawable).glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
    }
}
