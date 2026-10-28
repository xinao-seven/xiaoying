package com.xiaoying.material;

import com.xiaoying.auth.AuthUser;
import com.xiaoying.auth.CurrentUser;
import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialDto> upload(@RequestPart("file") MultipartFile file,
                                              @CurrentUser AuthUser user) {
        return ResponseEntity.status(201).body(materialService.upload(user.id(), file));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable("id") String id, @CurrentUser AuthUser user) {
        MaterialService.MaterialFile file = materialService.loadForUser(id, user.id());
        Path path = file.path();
        long length;
        try {
            length = Files.size(path);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MATERIAL_NOT_FOUND, "素材文件不存在");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CACHE_CONTROL, "private")
                .contentLength(length)
                .body(new FileSystemResource(path));
    }
}