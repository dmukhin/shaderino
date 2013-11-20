package dmukhin.jogl2_example;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.media.opengl.DebugGL2;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

public abstract class GlUtils {
    public static GL2 useDebugGl(GLAutoDrawable drawable, boolean debugGl) {
        GL2 gl = getGl2(drawable);
        if (debugGl && !(gl instanceof DebugGL2)) {
            gl = new DebugGL2(gl);
            drawable.setGL(gl);
        }
        return gl;
    }

    public static GL2 getGl2(GLAutoDrawable drawable) {
        return drawable.getGL().getGL2();
    }

    public static int createProgram(final GL2 gl,
            Map<String, Object> parameters, String logMessagePrefix,
            int... pixelShaders) {
        final int program = gl.glCreateProgram();

        for (int pixelShader : pixelShaders) {
            gl.glAttachShader(program, pixelShader);
        }

        gl.glLinkProgram(program);

        int[] programStatus = new int[1];
        gl.glGetProgramiv(program, GL2.GL_LINK_STATUS, programStatus, 0);
        if (GL2.GL_TRUE != programStatus[0] || logMessagePrefix != null) {
            String info = getInfoLog(new GetInfoLogAction() {
                @Override
                public void doGetInfoLog(byte[] buffer, int[] length) {
                    gl.glGetProgramInfoLog(program, buffer.length, length, 0,
                            buffer, 0);
                }
            });

            if (GL2.GL_TRUE != programStatus[0]) {
                throw new RuntimeException("program not linked, from log: "
                        + info);
            } else {
                System.out.println(logMessagePrefix + info);
            }
        }

        gl.glUseProgram(program);

        for (Entry<String, Object> entry : parameters.entrySet()) {
            Integer parameterLocation = getUniformLocation(gl, program,
                    entry.getKey());
            if (parameterLocation != null) {
                Object value = entry.getValue();
                if (value instanceof Number) {
                    Number numberValue = (Number) value;
                    if (value instanceof Float || value instanceof Double) {
                        gl.glUniform1f(parameterLocation,
                                numberValue.floatValue());
                    } else {
                        gl.glUniform1i(parameterLocation,
                                numberValue.intValue());
                    }
                } else {
                    throw new IllegalArgumentException("invalid parameter "
                            + entry.getKey() + " type "
                            + value.getClass().getName());
                }
            } else {
                throw new IllegalArgumentException(
                        "no uniform parameter found " + entry.getKey());
            }
        }

        return program;
    }

    public static int createShader(final GL2 gl, InputStream templateSource,
            boolean textureRectangle, String logMessagePrefix)
            throws IOException {
        ArrayList<String> lines = new ArrayList<String>();

        if (textureRectangle) {
            lines.add("#extension GL_ARB_texture_rectangle : enable\n");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                templateSource, "ASCII"));
        try {
            String line;
            do {
                line = reader.readLine();
                if (line != null) {
                    lines.add(line.replaceAll("\\$\\{Rect\\}",
                            textureRectangle ? "Rect" : "")
                            + "\n");
                }
            } while (line != null);
        } finally {
            reader.close();
        }

        int lineCount = lines.size();

        int[] lineLengths = new int[lineCount];
        for (int i = 0; i < lineCount; i++) {
            lineLengths[i] = lines.get(i).length();
        }

        final int shader = gl.glCreateShader(GL2.GL_FRAGMENT_SHADER);
        gl.glShaderSource(shader, lineCount,
                lines.toArray(new String[lineCount]), lineLengths, 0);
        gl.glCompileShader(shader);

        int[] shaderStatus = new int[1];
        gl.glGetShaderiv(shader, GL2.GL_COMPILE_STATUS, shaderStatus, 0);
        if (GL2.GL_TRUE != shaderStatus[0] || logMessagePrefix != null) {
            String info = getInfoLog(new GetInfoLogAction() {
                @Override
                public void doGetInfoLog(byte[] buffer, int[] length) {
                    gl.glGetShaderInfoLog(shader, buffer.length, length, 0,
                            buffer, 0);
                }
            });

            if (GL2.GL_TRUE != shaderStatus[0]) {
                throw new RuntimeException("shader not compiled, from log: "
                        + info);
            } else {
                System.out.println(logMessagePrefix + info);
            }
        }

        return shader;
    }

    public static Integer getUniformLocation(GL2 gl, int program, String uniform) {
        int uniformLocation = gl.glGetUniformLocation(program, uniform);
        return uniformLocation != -1 ? uniformLocation : null;
    }

    public static int getCurrentTexture(GL2 gl, int textureTarget) {
        int[] currentTexture = new int[1];
        gl.glGetIntegerv(textureTarget, currentTexture, 0);
        return currentTexture[0];
    }

    public static int createTexture(GL2 gl, int textureTarget) {
        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        int texture = textures[0];

        gl.glBindTexture(textureTarget, texture);
        gl.glTexParameteri(textureTarget, GL2.GL_TEXTURE_MIN_FILTER,
                GL2.GL_LINEAR);
        gl.glTexParameteri(textureTarget, GL2.GL_TEXTURE_MAG_FILTER,
                GL2.GL_LINEAR);
        gl.glTexParameteri(textureTarget, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
        gl.glTexParameteri(textureTarget, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);

        return texture;
    }

    public static Dimension loadBGRTexture(GL2 gl, int textureTarget,
            InputStream stream) throws IOException {
        BufferedImage image = loadBGRImage(stream);
        int width = image.getWidth();
        int height = image.getHeight();

        byte[] textureData = ((DataBufferByte) image.getRaster()
                .getDataBuffer()).getData();
        gl.glTexImage2D(textureTarget, 0, 3, width, height, 0, GL2.GL_BGR,
                GL2.GL_UNSIGNED_BYTE, ByteBuffer.wrap(textureData));

        return new Dimension(width, height);
    }

    public static void deleteTextures(GL2 gl, int... textures) {
        gl.glDeleteTextures(textures.length, textures, 0);
    }

    public static BufferedImage loadBGRImage(InputStream stream)
            throws IOException {
        BufferedImage loadedImage;
        try {
            loadedImage = ImageIO.read(stream);
        } finally {
            stream.close();
        }

        if (loadedImage != null) {
            BufferedImage convertedImage = new BufferedImage(
                    loadedImage.getWidth(), loadedImage.getHeight(),
                    BufferedImage.TYPE_3BYTE_BGR);
            convertedImage.getGraphics().drawImage(loadedImage, 0, 0, null);

            return convertedImage;
        } else {
            throw new RuntimeException("not supported image format");
        }
    }

    public static String getInfoLog(GetInfoLogAction getInfoLogAction) {
        byte[] buffer = new byte[16 * 1024];
        int[] length = new int[1];
        getInfoLogAction.doGetInfoLog(buffer, length);
        return new String(buffer, 0, Math.max(length[0] - 1, 0)).trim();
    }

    public interface GetInfoLogAction {
        void doGetInfoLog(byte[] buffer, int[] length);
    }
}
