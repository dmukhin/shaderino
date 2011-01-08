package test;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
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

    private Boolean vsync;

    private int maxFps = 100;

    private int animationSpeed = 50;

    private boolean showFps;

    private int showFpsPeriod = 10000;

    private String effect = "spot";

    private Map<String, Object> effectParameters = new HashMap<String, Object>();

    private String image = "cover";

    private LinkedHashMap<String, Map<String, Object>> postprocessors = new LinkedHashMap<String, Map<String, Object>>();

    private boolean pbuffer;

    private boolean textureRectangle = true;

    private boolean hdr;

    private boolean pbufferRenderToTexture;

    private Frame window;

    private Panel windowClientArea;

    private GLCanvas view;

    private SceneRenderer sceneRenderer;

    private GLEventListener actualRenderer;

    private volatile boolean closing;

    private volatile boolean paused;

    private boolean fullscreen;

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
		} else if (optionLowCase.matches("fullscreen")) {
		    fullscreen = true;
		} else if (optionLowCase.matches("vsync")) {
		    if (value.equalsIgnoreCase("on")) {
			vsync = true;
		    } else if (value.equalsIgnoreCase("off")) {
			vsync = false;
		    } else {
			throw new IllegalArgumentException(
				"invalid vsync option value");
		    }
		} else if (optionLowCase.matches("max\\-fps")) {
		    maxFps = Integer.parseInt(value);
		} else if (optionLowCase.matches("(animation\\-)speed")) {
		    animationSpeed = Integer.parseInt(value);
		} else if (optionLowCase.matches("show\\-fps")) {
		    showFps = true;
		    if (value != null) {
			showFpsPeriod = Integer.parseInt(value) * 1000;
		    }
		} else if (optionLowCase.matches("e(ffect)?")) {
		    if (value == null) {
			throw new IllegalArgumentException(
				"effect name not specified");
		    }

		    NameWithParameters effectWithParameters = NameWithParameters
			    .parse(value);
		    effect = effectWithParameters.getName();
		    effectParameters = effectWithParameters.getParameters();
		} else if (optionLowCase.matches("i(mage)?")) {
		    image = value;
		} else if (optionLowCase.matches("(p|pp|((post)?processor))")) {
		    if (value == null) {
			throw new IllegalArgumentException(
				"postprocessor name not specified");
		    }

		    NameWithParameters postprocessorWithParameters = NameWithParameters
			    .parse(value);
		    postprocessors.put(postprocessorWithParameters.getName(),
			    postprocessorWithParameters.getParameters());
		} else if (optionLowCase.matches("pbuffer")) {
		    pbuffer = true;
		} else if (optionLowCase
			.matches("pbuffer\\-(rtt|render\\-to\\-texture)")) {
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

	if (fullscreen) {
	    window.setUndecorated(true);

	    window.setExtendedState(Frame.MAXIMIZED_BOTH);

	    window.setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
		    new BufferedImage(1, 1, BufferedImage.TRANSLUCENT),
		    new Point(0, 0), "InvisibleCursor"));

	    // get rid of problem appears in Windows:
	    // if started in fullscreen, then Win key pressed, then appeared
	    // Windows menu hidden by mouse click out of menu, then Alt-Tab,
	    // then Alt-Tab back - since that it causes no non-system keys
	    // handled
	    window.addWindowListener(new WindowAdapter() {
		@Override
		public void windowActivated(WindowEvent vent) {
		    window.requestFocus();
		}
	    });
	}

	windowClientArea = new Panel();
	windowClientArea.setBackground(Color.BLACK);
	windowClientArea.setLayout(null);
	window.add(windowClientArea);

	window.addKeyListener(new KeyAdapter() {
	    @Override
	    public void keyPressed(KeyEvent event) {
		switch (event.getKeyCode()) {
		case KeyEvent.VK_ESCAPE:
		    closeWindow();
		    break;
		case KeyEvent.VK_PAUSE:
		    paused = !paused;
		    break;
		}
	    }
	});

	window.addWindowListener(new WindowAdapter() {
	    @Override
	    public void windowClosing(WindowEvent event) {
		closeWindow();
	    }
	});
    }

    private void closeWindow() {
	closing = true;

	window.dispose();
    }

    private void createView() {
	view = new GLCanvas();

	sceneRenderer = new SceneRenderer();
	sceneRenderer.setDebugGl(debugGl);
	sceneRenderer.setEffect(effect);
	sceneRenderer.setEffectParameters(effectParameters);
	sceneRenderer.setImage(image);
	sceneRenderer.setTextureRectangle(textureRectangle);
	sceneRenderer.setRotationAngle(0);

	actualRenderer = sceneRenderer;
	for (Entry<String, Map<String, Object>> postprocessorEntry : postprocessors
		.entrySet()) {
	    actualRenderer = createScenePostprocessor(postprocessorEntry
		    .getKey(), actualRenderer, postprocessorEntry.getValue());
	}

	view.addGLEventListener(new GLEventListener() {
	    private long fpsMeasureStartTime;

	    private int frameCount;

	    @Override
	    public void init(GLAutoDrawable drawable) {
		printGlInfo(GlUtils.getGl2(drawable));

		restartFpsMeasure();

		actualRenderer.init(drawable);

		if (vsync != null) {
		    GlUtils.getGl2(drawable).setSwapInterval(vsync ? 1 : 0);
		}
	    }

	    @Override
	    public void reshape(GLAutoDrawable drawable, int x, int y,
		    int width, int height) {
		actualRenderer.reshape(drawable, x, y, width, height);
	    }

	    @Override
	    public void display(GLAutoDrawable drawable) {
		actualRenderer.display(drawable);

		if (showFps) {
		    frameCount++;

		    if (System.currentTimeMillis() - fpsMeasureStartTime >= showFpsPeriod) {
			showFps();

			restartFpsMeasure();
		    }
		}
	    }

	    @Override
	    public void dispose(GLAutoDrawable drawable) {
		actualRenderer.dispose(drawable);
	    }

	    private void restartFpsMeasure() {
		if (showFps) {
		    fpsMeasureStartTime = System.currentTimeMillis();
		    frameCount = 0;
		}
	    }

	    private void showFps() {
		System.out.format("FPS was %.1f in last %d seconds\n",
			((double) frameCount) * 1000 / showFpsPeriod,
			showFpsPeriod / 1000);
	    }
	});

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
	    String postprocessorName, GLEventListener actualRenderer,
	    Map<String, Object> postprocessorParameters) {
	ScenePostprocessor scenePostprocessor = pbuffer ? new PbufferScenePostprocessor()
		: new FboScenePostprocessor();
	scenePostprocessor.setSceneRenderer(actualRenderer);
	scenePostprocessor.setPostprocessor(postprocessorName);
	scenePostprocessor.setPostprocessorParameters(postprocessorParameters);
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
	Dimension windowClientAreaSize = windowClientArea.getSize();
	double windowWidth = windowClientAreaSize.getWidth();
	double windowHeight = windowClientAreaSize.getHeight();
	int viewSide = (int) Math.min(windowWidth, windowHeight);
	view.setBounds((int) (windowWidth - viewSide) / 2,
		(int) (windowHeight - viewSide) / 2, viewSide, viewSide);
    }

    private void animate() {
	closing = false;

	window.setVisible(true);

	long animationPeriod = 1000l * 1000 * 1000 / maxFps;
	long animationPeriodMillis = animationPeriod / (1000 * 1000);
	int animationPeriodNanos = (int) (animationPeriod % (1000 * 1000));

	float rotationAngle = 0;
	float rotationAngleDelta = animationPeriod * animationSpeed / 1e9f;
	while (!closing) {
	    boolean wasPaused = paused;
	    try {
		Thread.sleep(animationPeriodMillis, animationPeriodNanos);
	    } catch (InterruptedException exception) {
	    }

	    if (!closing) {
		if (!paused && !wasPaused) {
		    rotationAngle += rotationAngleDelta;
		    if (rotationAngle >= 180) {
			rotationAngle = rotationAngle - 360;
		    }

		    sceneRenderer.setRotationAngle(rotationAngle);
		}
	    }

	    view.repaint();
	}
    }

    private static class NameWithParameters {
	private static final Pattern SPECIFICATION_PATTERN = Pattern
		.compile("(.*?)(?:\\((.*)\\))?");

	private static final Pattern PAIR_PATTERN = Pattern
		.compile("(.+)[\\:=](.+)");

	private final String name;

	private final Map<String, Object> parameters;

	static NameWithParameters parse(String specification) {
	    Matcher specificationMatcher = SPECIFICATION_PATTERN
		    .matcher(specification);
	    specificationMatcher.matches();

	    String name = specificationMatcher.group(1);

	    Map<String, Object> parameters = new HashMap<String, Object>();
	    if (specificationMatcher.groupCount() > 1) {
		String parameterString = specificationMatcher.group(2);
		if (parameterString != null) {
		    for (String pair : parameterString.split("[;,]")) {
			Matcher pairMatcher = PAIR_PATTERN.matcher(pair);
			if (pairMatcher.matches()) {
			    parameters.put(pairMatcher.group(1),
				    parseValue(pairMatcher.group(2)));
			} else {
			    throw new IllegalArgumentException(
				    "illegal parameter specification for "
					    + name);
			}
		    }
		}
	    }

	    return new NameWithParameters(name, parameters);
	}

	private NameWithParameters(String name, Map<String, Object> parameters) {
	    this.name = name;
	    this.parameters = parameters;
	}

	String getName() {
	    return name;
	}

	Map<String, Object> getParameters() {
	    return parameters;
	}

	private static Object parseValue(String value) {
	    if (value.contains(".")) {
		return Float.parseFloat(value);
	    } else {
		return Integer.parseInt(value);
	    }
	}
    }
}
