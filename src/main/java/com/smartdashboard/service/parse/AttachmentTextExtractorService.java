package com.smartdashboard.service.parse;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class AttachmentTextExtractorService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentTextExtractorService.class);
    private final Tika tika = new Tika();

    public String extractText(byte[] data, String filename) {
        if (data == null || data.length == 0) return "";
        try (InputStream is = new ByteArrayInputStream(data)) {
            return extractText(is, filename);
        } catch (Exception e) {
            log.warn("Failed to extract text from byte array of {}: {}", filename, e.getMessage());
            return "";
        }
    }

    public String extractText(InputStream inputStream, String filename) {
        if (inputStream == null) return "";
        try {
            String text = tika.parseToString(inputStream);
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            log.warn("Tika text extraction failed for attachment {}: {}", filename, e.getMessage());
            return "";
        }
    }
}
