package com.store.visualsearch.service;

import com.store.product.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSearchServiceTest {
    @Test void rejectsUnsupportedMimeTypeBeforeRepositoryAccess() {
        ProductRepository repository=Mockito.mock(ProductRepository.class);
        VisualSearchService service=new VisualSearchService(
                repository,
                Mockito.mock(VisualSearchCandidateService.class),
                Mockito.mock(VisualImageHashService.class),
                Mockito.mock(SecureImageFetcher.class),
                new VisualSearchProperties(10*1024*1024,200,6,2500,10*1024*1024,60));

        MockMultipartFile file=new MockMultipartFile(
                "image","payload.svg","image/svg+xml","<svg/>".getBytes());

        assertThrows(RuntimeException.class,()->service.search(file));
        Mockito.verifyNoInteractions(repository);
    }

    @Test void acceptsWebpUploadAccordingToStorefrontContract() {
        ProductRepository repository=Mockito.mock(ProductRepository.class);
        VisualSearchCandidateService candidateService=Mockito.mock(VisualSearchCandidateService.class);
        VisualImageHashService imageHashService=Mockito.mock(VisualImageHashService.class);
        SecureImageFetcher fetcher=Mockito.mock(SecureImageFetcher.class);
        VisualSearchService service=new VisualSearchService(
                repository,
                candidateService,
                imageHashService,
                fetcher,
                new VisualSearchProperties(10*1024*1024,200,6,2500,10*1024*1024,60));

        assertTrue(ImageIO.getImageReadersByMIMEType("image/webp").hasNext(),
                "The WebP ImageIO provider must be present on the runtime classpath");

        byte[] payload=new byte[]{1,2,3,4};
        MockMultipartFile file=new MockMultipartFile(
                "image","product-photo.webp","image/webp",payload);
        Mockito.when(imageHashService.hash(Mockito.any(byte[].class)))
                .thenReturn(new long[]{1L,2L,3L,4L});
        Mockito.when(candidateService.loadCandidates()).thenReturn(List.of());

        assertTrue(service.search(file).isEmpty());
        Mockito.verify(imageHashService).hash(Mockito.any(byte[].class));
        Mockito.verify(candidateService).loadCandidates();
    }

    @Test
    void legacyConstructorFailsClearlyWhenUsedForAnActualSearch() {
        VisualSearchService service = new VisualSearchService(
                Mockito.mock(ProductRepository.class),
                Mockito.mock(VisualImageHashService.class),
                Mockito.mock(SecureImageFetcher.class),
                new VisualSearchProperties(10*1024*1024,200,6,2500,10*1024*1024,60));

        MockMultipartFile validMimeFile = new MockMultipartFile(
                "image", "query.jpg", "image/jpeg", new byte[]{1});

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> service.search(validMimeFile));
    }

    @Test void rejectsOversizedImageBeforeRepositoryAccess() {
        ProductRepository repository=Mockito.mock(ProductRepository.class);
        VisualSearchService service=new VisualSearchService(
                repository,
                Mockito.mock(VisualSearchCandidateService.class),
                Mockito.mock(VisualImageHashService.class),
                Mockito.mock(SecureImageFetcher.class),
                new VisualSearchProperties(10*1024*1024,200,6,2500,10*1024*1024,60));

        byte[] payload=new byte[10*1024*1024+1];
        MockMultipartFile file=new MockMultipartFile("image","large.jpg","image/jpeg",payload);

        assertThrows(RuntimeException.class,()->service.search(file));
        Mockito.verifyNoInteractions(repository);
    }
}

