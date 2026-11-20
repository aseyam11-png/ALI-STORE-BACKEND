package com.store.visualsearch.service;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class VisualImageHashServiceTest {
    private final VisualImageHashService service = new VisualImageHashService();

    @Test void identicalImagesProduceFullSimilarity() {
        BufferedImage image = imageWithPattern(false);
        long[] first = service.hash(toPng(image));
        long[] second = service.hash(toPng(image));
        assertEquals(100, service.similarity(first, second));
    }

    @Test void differentImagesProduceLowerSimilarity() {
        long[] first = service.hash(toPng(imageWithPattern(false)));
        long[] second = service.hash(toPng(imageWithPattern(true)));
        assertTrue(service.similarity(first, second) < 100);
    }

    @Test void rejectsInvalidImageBytes() {
        assertThrows(RuntimeException.class, () -> service.hash("not-an-image".getBytes()));
    }

    private BufferedImage imageWithPattern(boolean inverted) {
        BufferedImage image = new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();
        try {
            graphics.setColor(inverted?Color.WHITE:Color.BLACK); graphics.fillRect(0,0,64,64);
            graphics.setColor(inverted?Color.BLACK:Color.WHITE); graphics.fillRect(0,0,32,64);
        } finally { graphics.dispose(); }
        return image;
    }

    private byte[] toPng(BufferedImage image) {
        try(var output=new java.io.ByteArrayOutputStream()){
            javax.imageio.ImageIO.write(image,"png",output); return output.toByteArray();
        }catch(java.io.IOException ex){ throw new AssertionError(ex); }
    }
}
