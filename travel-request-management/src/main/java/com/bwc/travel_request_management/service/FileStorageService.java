package com.bwc.travel_request_management.service;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;


import java.io.InputStream;

public interface FileStorageService {

    /**
     * Uploads a file to the storage system.
     *
     * @param fileName    the name to assign to the uploaded file
     * @param inputStream the file content as InputStream
     * @param contentType the MIME type (e.g., "application/pdf", "image/png")
     * @return the public or accessible URL of the uploaded file
     */
    String uploadFile(String fileName, InputStream inputStream, String contentType);

    /**
     * Downloads a file from the storage system.
     *
     * @param fileName the name of the file to download
     * @return the file content as InputStream
     */
    InputStream downloadFile(String fileName);

    /**
     * Deletes a file from the storage system.
     *
     * @param fileName the name of the file to delete
     */
    void deleteFile(String fileName);
}
