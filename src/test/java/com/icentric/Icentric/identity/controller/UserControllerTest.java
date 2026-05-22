package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.identity.dto.BulkUploadConfirmRequest;
import com.icentric.Icentric.identity.dto.BulkUploadResponse;
import com.icentric.Icentric.identity.dto.BulkUploadValidateResponse;
import com.icentric.Icentric.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    void getBulkUploadTemplate_returnsAttachmentWithExpectedCsvHeaderAndSampleRows() {
        String expectedCsv = "name,email,role,department\n" +
                "Jane Doe,jane.doe@example.com,LEARNER,Engineering\n" +
                "John Smith,john.smith@example.com,ADMIN,Sales\n";
        when(userService.getBulkUploadTemplateCsv()).thenReturn(expectedCsv);

        ResponseEntity<String> response = userController.getBulkUploadTemplate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=tenant-user-bulk-upload-template.csv");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("text/csv"));
        assertThat(response.getBody()).isEqualTo(expectedCsv);
    }

    @Test
    void getBulkUploadInstructionsPdf_returnsAttachmentWithPdfContent() {
        byte[] expectedPdf = "dummy-pdf-content".getBytes();
        when(userService.getBulkUploadInstructionsPdf()).thenReturn(expectedPdf);

        ResponseEntity<byte[]> response = userController.getBulkUploadInstructionsPdf();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=tenant-user-bulk-upload-guide.pdf");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isEqualTo(expectedPdf);
    }

    @Test
    void validateUpload_delegatesToService() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "content".getBytes());
        BulkUploadValidateResponse expected = new BulkUploadValidateResponse(0, 0, 0, Collections.emptyList());
        when(userService.validateBulkUpload(file)).thenReturn(expected);

        BulkUploadValidateResponse response = userController.validateUpload(file);

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void confirmUpload_delegatesToService() {
        BulkUploadConfirmRequest request = new BulkUploadConfirmRequest(Collections.emptyList(), true);
        BulkUploadResponse expected = new BulkUploadResponse(0, 0, 0, Collections.emptyList());
        when(userService.confirmBulkUpload(request)).thenReturn(expected);

        BulkUploadResponse response = userController.confirmUpload(request);

        assertThat(response).isEqualTo(expected);
    }
}

