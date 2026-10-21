package com.certitrack.service;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.CertificateFilter;
import jakarta.validation.Valid;
import java.io.*;

public interface CsvExportService {
    void export(SessionIdentity identity, @Valid CertificateFilter filter, Writer writer) throws IOException;
}
