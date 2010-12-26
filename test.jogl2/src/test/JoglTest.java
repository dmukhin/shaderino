package test;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;

public class JoglTest {
    private static final Pattern OPTION_PATTERN = Pattern
	    .compile("(?:/|-{1,2})(.+?)(?:[=\\:](.+))?");

    private boolean debugGl;

    private String effect = "spotglow";

    private String image = "cover";

    private String[] postprocessors = {};

    private boolean pbuffer;

    private boolean textureRectangle = true;

    private boolean hdr;

    private boolean pbufferRenderToTexture;

    private Frame window;

    private Panel windowClientArea;

    private GLCanvas view;

    private SceneRenderer sceneRenderer;

    private volatile boolean closing;

    public static void main(String[] args) throws Exception {
	JoglTest test = new JoglTest();
	test.processArgs(args);
	test.execute(args);
    }

    public void execute(String[] args) throws Exception {
	createWindow();

	createView();

	animate();
    }

    public void processArgs(String[] args) {
	for (String arg : args) {
	    Matcher matcher = OPTION_PATTERN.matcher(arg);
	    if (matcher.matches()) {
		String option = matcher.group(1);
		String optionLowCase = option.toLowerCase();
		String value = matcher.groupCount() > 1 ? matcher.group(2)
			: null;
		if (optionLowCase.matches("d(ebug)?")) {
		    debugGl = true;
		} else if (optionLowCase.matches("e(ffect)?")) {
		    effect = value;
		} else if (optionLowCase.matches("i(mage)?")) {
		    image = value;
		} else if (optionLowCase.matches("(p|pp|((post)?processors))")) {
		    postprocessors = value.split(",");
		} else if (optionLowCase.matches("pbuffer")) {
		    pbuffer = true;
		} else if (optionLowCase
			.matches("pbuffer-(rtt|render\\-to\\-texture)")) {
		    pbuffer = true;
		    pbufferRenderToTexture = true;
		} else if (optionLowCase.matches("hdr|high\\-dynamic\\-range")) {
		    hdr = true;
		} else if (optionLowCase.matches("texture\\-2d")) {
		    textureRectangle = false;
		} else {
		    throw new IllegalArgumentException("invalid option: "
			    + option);
		}
	    } else {
		throw new IllegalArgumentException("invalid parameter: " + arg);
	    }
	}
    }

    private void createWindow() {
	window = new Frame("JOGL 2 Test");
	window.setLocationByPlatform(true);
	window.setSize(400, 400);
	window.setLayout(new BorderLayout());

	windowClientArea = new Panel();
	windowClientArea.setBackground(Color.BLACK);
	windowClientArea.setLayout(null);
	window.add(windowClientArea);

	window.addWindowListener(new WindowAdapter() {
	    @Override
	    public void windowClosing(WindowEvent event) {
		closing = true;

		event.getWindow().dispose();
	    }
	});
    }

    private void createView() {
	view = new GLCanvas();

	view.addGLEventListener(new GLEventListener() {
	    @Override
	    public void init(GLAutoDrawable drawable) {
		printGlInfo(GlUtils.getGl2(drawable));
	    }

	    @Override
	    public void reshape(GLAutoDrawable drawable, int x, int y,
		    int width, int height) {
	    }

	    @Override
	    public void display(GLAutoDrawable glautodrawable) {
	    }

	    @Override
	    public void dispose(GLAutoDrawable glautodrawable) {
	    }
	});

	sceneRenderer = new SceneRenderer();
	sceneRenderer.setDebugGl(debugGl);
	sceneRenderer.setEffect(effect);
	sceneRenderer.setImage(image);
	sceneRenderer.setTextureRectangle(textureRectangle);
	sceneRenderer.setRotationAngle(0);

	GLEventListener actualRenderer = sceneRenderer;
	for (String postprocessorName : postprocessors) {
	    actualRenderer = createScenePostprocessor(postprocessorName,
		    actualRenderer);
	}

	view.addGLEventListener(actualRenderer);

	windowClientArea.addComponentListener(new ComponentAdapter() {
	    @Override
	    public void componentResized(ComponentEvent event) {
		resizeView();
	    }
	});

	windowClientArea.add(view);
	resizeView();
    }

    private ScenePostprocessor createScenePostprocessor(
	    String postprocessorName, GLEventListener actualRenderer) {
	ScenePostprocessor scenePostprocessor = pbuffer ? new PbufferScenePostprocessor()
		: new FboScenePostprocessor();
	scenePostprocessor.setSceneRenderer(actualRenderer);
	scenePostprocessor.setPostprocessor(postprocessorName);
	scenePostprocessor.setDebugGl(debugGl);
	scenePostprocessor.setTextureRectangle(textureRectangle);
	scenePostprocessor.setHdr(hdr);
	if (pbuffer) {
	    ((PbufferScenePostprocessor) scenePostprocessor)
		    .setRenderToTexture(pbufferRenderToTexture);
	}

	return scenePostprocessor;
    }

    private void printGlInfo(GL2 gl) {
	System.out.format("GL vendor:\t%s\n", gl.glGetString(GL2.GL_VENDOR));
	System.out.format("GL version:\t%s\n", gl.glGetString(GL2.GL_VERSION));
	System.out.format("GLSL version:\t%s\n", gl
		.glGetString(GL2.GL_SHADING_LANGUAGE_VERSION));
    }

    private void resizeView() {
	Dimension windowPreferredSize = windowClientArea.getSize();
	double windowWidth = windowPreferredSize.getWidth();
	double windowHeight = windowPreferredSize.getHeight();
	int viewSide = (int) Math.min(windowWidth, windowHeight);
	view.setBounds((int) (windowWidth - viewSide) / 2,
		(int) (windowHeight - viewSide) / 2, viewSide, viewSide);
    }

    private void animate() {
	closing = false;

	int rotationAngle = 0;
	window.setVisible(true);

	while (!closing) {
	    try {
		Thread.sleep(1000 / 50);
	    } catch (InterruptedException exception) {
	    }

	    if (!closing) {
		rotationAngle++;
		if (rotationAngle >= 180) {
		    rotationAngle = -180;
		}

		sceneRenderer.setRotationAngle(rotationAngle);
		view.repaint();
	    }
	}
    }
}
