package com.store.visualsearch.service;

import com.store.common.exception.BusinessException;
import com.store.product.entity.Product;
import com.store.product.entity.ProductImage;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.product.repository.ProductSpecifications;
import com.store.visualsearch.dto.VisualSearchResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VisualSearchService {
    private static final List<String> SUPPORTED_TYPES=List.of("image/jpeg","image/png");
    private static final int MAX_CACHE_ENTRIES = 5000;
    private static final long SUCCESS_CACHE_TTL_MS = 6 * 60 * 60 * 1000L;
    private static final long FAILURE_CACHE_TTL_MS = 2 * 60 * 1000L;
    private final ProductRepository productRepository;
    private final VisualImageHashService imageHashService;
    private final SecureImageFetcher imageFetcher;
    private final VisualSearchProperties properties;
    private final Map<String,CachedHash> hashCache=new ConcurrentHashMap<>();

    public VisualSearchService(ProductRepository productRepository,VisualImageHashService imageHashService,
                               SecureImageFetcher imageFetcher,VisualSearchProperties properties){
        this.productRepository=productRepository;this.imageHashService=imageHashService;
        this.imageFetcher=imageFetcher;this.properties=properties;
    }

    public List<VisualSearchResult> search(MultipartFile image){
        validateUpload(image);
        byte[] uploadedBytes;
        try{uploadedBytes=image.getBytes();}catch(IOException ex){throw new BusinessException("Unable to read the uploaded image");}
        long[] queryHash=imageHashService.hash(uploadedBytes);

        Specification<Product> spec=Specification.where(ProductSpecifications.hasStatus(ProductStatus.ACTIVE))
                .and(ProductSpecifications.hasActiveCategory()).and(ProductSpecifications.hasActiveSupplier());
        List<Product> candidates=productRepository.findAll(spec,PageRequest.of(0,properties.maxCandidates(),
                Sort.by(Sort.Direction.ASC,"id"))).getContent();

        List<VisualSearchResult> results=new ArrayList<>();
        for(Product product:candidates){
            ProductImage imageRef=product.getImages().stream().filter(ProductImage::getIsPrimary).findFirst()
                    .orElseGet(()->product.getImages().stream().min(Comparator.comparing(ProductImage::getSortOrder)).orElse(null));
            if(imageRef==null||imageRef.getUrl()==null||imageRef.getUrl().isBlank())continue;
            long[] productHash=getOrComputeHash(imageRef.getUrl());
            if(productHash==null)continue;
            int similarity=imageHashService.similarity(queryHash,productHash);
            if(similarity<properties.minSimilarity())continue;
            results.add(VisualSearchResult.builder().productId(product.getId()).productName(product.getName())
                    .image(imageRef.getUrl()).similarity(similarity)
                    .price(product.getPrice()).currency(product.getCurrency())
                    .brand(product.getBrand()==null?"":product.getBrand().getName())
                    .category(product.getCategory().getName()).build());
        }
        return results.stream().sorted(Comparator.comparingInt(VisualSearchResult::similarity).reversed()
                .thenComparing(VisualSearchResult::productId)).limit(properties.maxResults()).toList();
    }

    private void validateUpload(MultipartFile image){
        if(image==null||image.isEmpty())throw new BusinessException("An image is required");
        if(image.getSize()>properties.maxFileBytes())throw new BusinessException("Image must be 10 MB or less");
        if(!SUPPORTED_TYPES.contains(image.getContentType()))throw new BusinessException("Only JPG, PNG and WEBP images are supported");
    }

    private long[] getOrComputeHash(String url){
        CachedHash cached=hashCache.get(url);
        if(cached!=null&&!cached.expired())return cached.hash();
        byte[] bytes=imageFetcher.fetch(url);
        long now=System.currentTimeMillis();
        if(bytes==null||bytes.length==0){
            cache(url,new CachedHash(null,now,FAILURE_CACHE_TTL_MS));
            return null;
        }
        try{
            long[] hash=imageHashService.hash(bytes);
            cache(url,new CachedHash(hash,now,SUCCESS_CACHE_TTL_MS));
            return hash;
        }catch(BusinessException ex){
            cache(url,new CachedHash(null,now,FAILURE_CACHE_TTL_MS));
            return null;
        }
    }

    private void cache(String url,CachedHash value){
        if(hashCache.size()>=MAX_CACHE_ENTRIES){
            hashCache.entrySet().removeIf(entry->entry.getValue().expired());
            if(hashCache.size()>=MAX_CACHE_ENTRIES){
                String key=hashCache.keys().hasMoreElements()?hashCache.keys().nextElement():null;
                if(key!=null)hashCache.remove(key);
            }
        }
        hashCache.put(url,value);
    }

    private record CachedHash(long[] hash,long createdAt,long ttlMillis){
        boolean expired(){return System.currentTimeMillis()-createdAt>ttlMillis;}
    }
}
