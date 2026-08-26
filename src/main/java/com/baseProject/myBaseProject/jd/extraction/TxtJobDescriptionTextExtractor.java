package com.baseProject.myBaseProject.jd.extraction;

import com.baseProject.myBaseProject.exception.JobDescriptionFileCorruptedException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidFileTypeException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidTextException;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

@Component
public class TxtJobDescriptionTextExtractor implements JobDescriptionTextExtractor {

    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    @Override
    public JobDescriptionFileType fileType() {
        return JobDescriptionFileType.TXT;
    }

    @Override
    public String extract(byte[] content) {
        if (startsWithPdfMagic(content)) {
            throw new JobDescriptionInvalidFileTypeException();
        }

        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            if (text.indexOf('\0') >= 0) {
                throw new JobDescriptionFileCorruptedException();
            }
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw JobDescriptionInvalidTextException.invalidUtf8();
        }
    }

    private boolean startsWithPdfMagic(byte[] content) {
        if (content.length < PDF_MAGIC.length) {
            return false;
        }
        for (int index = 0; index < PDF_MAGIC.length; index++) {
            if (content[index] != PDF_MAGIC[index]) {
                return false;
            }
        }
        return true;
    }
}
