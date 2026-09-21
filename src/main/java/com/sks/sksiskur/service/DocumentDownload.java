package com.sks.sksiskur.service;

import org.springframework.core.io.Resource;

public record DocumentDownload(Resource resource, String filename, String contentType) {
}
