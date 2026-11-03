package michelle.zaca_bus.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.EncodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/** Código QR del boleto: lo que dibuja la taquilla y lo que lee la cámara del abordaje. El QR lleva el folio impreso. */
public final class Qr {

    private Qr() {
    }

    /** Imagen cuadrada de `lado` píxeles con el QR de `contenido`. */
    public static Image generar(String contenido, int lado) {
        try {
            BitMatrix puntos = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, lado, lado,
                    Map.of(EncodeHintType.MARGIN, 1));
            WritableImage imagen = new WritableImage(lado, lado);
            PixelWriter pixeles = imagen.getPixelWriter();
            for (int y = 0; y < lado; y++) {
                for (int x = 0; x < lado; x++) {
                    pixeles.setColor(x, y, puntos.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return imagen;
        } catch (WriterException e) {
            throw new IllegalArgumentException("No se pudo generar el QR de " + contenido, e);
        }
    }

    /** Texto del QR que aparece en la imagen; vacío si no hay ninguno legible. */
    public static Optional<String> leer(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int[] pixeles = imagen.getRGB(0, 0, ancho, alto, null, 0, ancho);
        BinaryBitmap mapa = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(ancho, alto, pixeles)));
        try {
            return Optional.of(new QRCodeReader().decode(mapa).getText());
        } catch (NotFoundException | ChecksumException | FormatException e) {
            return Optional.empty();
        }
    }
}
