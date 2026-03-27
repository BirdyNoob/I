package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.learning.exception.CertificateGenerationException;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class CertificatePdfService {
    private static final DateTimeFormatter ISSUE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu").withZone(ZoneOffset.UTC);

    public byte[] generateCertificate(

            String userEmail,
            String trackTitle,
            Instant issuedAt

    ) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28);
            Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 14);
            Font trackFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);

            Paragraph title = new Paragraph("CERTIFICATE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph("\n"));

            Paragraph certifies = new Paragraph("This certifies that", bodyFont);
            certifies.setAlignment(Element.ALIGN_CENTER);
            document.add(certifies);

            Paragraph learner = new Paragraph(userEmail, nameFont);
            learner.setAlignment(Element.ALIGN_CENTER);
            document.add(learner);

            Paragraph completed = new Paragraph("has successfully completed", bodyFont);
            completed.setAlignment(Element.ALIGN_CENTER);
            document.add(completed);

            Paragraph training = new Paragraph(trackTitle, trackFont);
            training.setAlignment(Element.ALIGN_CENTER);
            document.add(training);

            document.add(new Paragraph("\n"));

            Paragraph issued = new Paragraph("Issued on: " + ISSUE_DATE_FORMAT.format(issuedAt), bodyFont);
            issued.setAlignment(Element.ALIGN_CENTER);
            document.add(issued);

            document.close();

            return out.toByteArray();

        } catch (DocumentException e) {
            throw new CertificateGenerationException(
                    "Failed to generate certificate PDF for userEmail: " + userEmail + ", trackTitle: " + trackTitle,
                    e
            );
        }
    }
}
