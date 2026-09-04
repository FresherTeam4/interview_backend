package com.baseProject.myBaseProject.util.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PdfImageRenderer {

    private static final int DEFAULT_DPI = 150;
    private static final int MAX_PAGES = 5; // giới hạn số trang đọc tối đa

    public List<byte[]> renderPagesAsImages(byte[] pdfBytes) throws IOException {
        return renderPagesAsImages(pdfBytes, MAX_PAGES, DEFAULT_DPI);
    }

    public List<byte[]> renderPagesAsImages(byte[] pdfBytes, int maxPages, int dpi) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return List.of();
        }

        List<byte[]> pageImages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            int pagesToRender = Math.min(totalPages, maxPages);

            log.info("Rendering {} / {} pages of PDF to PNG images at {} DPI", pagesToRender, totalPages, dpi);

            for (int pageIndex = 0; pageIndex < pagesToRender; pageIndex++) {
                BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(bufferedImage, "PNG", baos);
                    pageImages.add(baos.toByteArray());
                }
            }
        }

        return pageImages;
    }
}
