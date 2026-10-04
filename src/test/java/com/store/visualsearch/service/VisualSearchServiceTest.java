package com.store.visualsearch.service;

import com.store.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisualSearchServiceTest {
    @Test void rejectsUnsupportedMimeTypeBeforeRepositoryAccess() {
        ProductRepository repository=Mockito.mock(ProductRepository.class);
        VisualSearchService service=new VisualSearchService(
                repository,
                Mockito.mock(VisualImageHashService.class),
                Mockito.mock(SecureImageFetcher.class),
                new VisualSearchProperties(10*1024*1024,200,6,2500,10*1024*1024));

        MockMultipartFile file=new MockMultipartFile(
                "image","payload.svg","image/svg+xml","<svg/>".getBytes());

        assertThrows(RuntimeException.class,()->service.search(file));
        Mockito.verifyNoInteractions(repository);
    }

    @Test void rejectsOversizedImageBeforeRepositoryAccess() {
        ProductRepository repository=Mockito.mock(ProductRepository.class);
        VisualSearchService service=new VisualSearchService(
                repository,
                Mockito.mock(VisualImageHashService.class),
                Mockito.mock(SecureImageFetcher.class),
                new VisualSearchProperties(10*1024*1024,200,6,2500,10*1024*1024));

        byte[] payload=new byte[10*1024*1024+1];
        MockMultipartFile file=new MockMultipartFile("image","large.jpg","image/jpeg",payload);

        assertThrows(RuntimeException.class,()->service.search(file));
        Mockito.verifyNoInteractions(repository);
    }
}
