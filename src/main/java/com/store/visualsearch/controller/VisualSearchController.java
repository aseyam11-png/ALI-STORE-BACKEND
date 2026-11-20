package com.store.visualsearch.controller;

import com.store.common.response.ApiResponse;
import com.store.visualsearch.dto.VisualSearchResult;
import com.store.visualsearch.service.VisualSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/visual-search")
@RequiredArgsConstructor
public class VisualSearchController {
    private final VisualSearchService visualSearchService;

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<VisualSearchResult>>> search(@RequestParam("image") MultipartFile image){
        return ResponseEntity.ok(ApiResponse.success(visualSearchService.search(image),"Visual search completed successfully"));
    }
}
