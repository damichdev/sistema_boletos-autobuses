package michelle.zaca_bus.view;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;
import com.github.sarxos.webcam.WebcamResolution;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import michelle.zaca_bus.util.Qr;

/**
 * Lee códigos QR con la cámara de la computadora y muestra lo que ve en `vista`. Captura en un hilo aparte; los
 * avisos llegan en el hilo de JavaFX. Se detiene solo si la pantalla ya no se ve (se navegó a otra).
 */
public final class EscanerQr {

    /** Un mismo QR frente a la cámara se avisa una vez cada tanto, no en cada cuadro. */
    private static final long REPETIR_MS = 3000;

    private final ImageView vista;
    private final Consumer<String> alLeer;
    private final Consumer<String> alFallar;
    private volatile boolean activo;
    private Thread hilo;

    public EscanerQr(ImageView vista, Consumer<String> alLeer, Consumer<String> alFallar) {
        this.vista = vista;
        this.alLeer = alLeer;
        this.alFallar = alFallar;
    }

    public boolean activo() {
        return activo;
    }

    public void iniciar() {
        activo = true;
        if (hilo == null || !hilo.isAlive()) {
            hilo = new Thread(this::capturar, "escaner-qr");
            hilo.setDaemon(true);
            hilo.start();
        }
    }

    public void detener() {
        activo = false;
    }

    private void capturar() {
        Webcam camara = Webcam.getDefault();
        if (camara == null) {
            fallar("No se encontró una cámara conectada.");
            return;
        }
        try {
            camara.setViewSize(WebcamResolution.VGA.getSize());
            camara.open();
            String ultimo = null;
            long cuando = 0;
            while (activo) {
                BufferedImage cuadro = camara.getImage();
                if (cuadro == null) {
                    continue;
                }
                WritableImage foto = aImagen(cuadro);
                Platform.runLater(() -> {
                    if (vista.getScene() == null) {
                        activo = false;
                    } else {
                        vista.setImage(foto);
                    }
                });
                String texto = Qr.leer(cuadro).orElse(null);
                if (texto != null && (!texto.equals(ultimo) || System.currentTimeMillis() - cuando > REPETIR_MS)) {
                    ultimo = texto;
                    cuando = System.currentTimeMillis();
                    Platform.runLater(() -> alLeer.accept(texto));
                }
            }
        } catch (WebcamException e) {
            fallar("No se pudo usar la cámara: " + e.getMessage());
        } finally {
            activo = false;
            camara.close();
        }
    }

    private void fallar(String aviso) {
        activo = false;
        Platform.runLater(() -> alFallar.accept(aviso));
    }

    private static WritableImage aImagen(BufferedImage cuadro) {
        int ancho = cuadro.getWidth();
        int alto = cuadro.getHeight();
        WritableImage imagen = new WritableImage(ancho, alto);
        imagen.getPixelWriter().setPixels(0, 0, ancho, alto, PixelFormat.getIntArgbInstance(),
                cuadro.getRGB(0, 0, ancho, alto, null, 0, ancho), 0, ancho);
        return imagen;
    }
}
