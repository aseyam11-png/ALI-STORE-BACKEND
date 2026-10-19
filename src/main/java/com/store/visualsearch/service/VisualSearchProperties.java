package com.store.visualsearch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VisualSearchProperties {
    private final int maxFileBytes, maxCandidates, maxResults, timeoutMs, maxBytes, minSimilarity;

    public VisualSearchProperties(
            @Value("${app.visual-search.max-file-bytes:10485760}") int maxFileBytes,
            @Value("${app.visual-search.max-candidates:200}") int maxCandidates,
            @Value("${app.visual-search.max-results:6}") int maxResults,
            @Value("${app.visual-search.remote-timeout-ms:2500}") int timeoutMs,
            @Value("${app.visual-search.remote-max-bytes:10485760}") int maxBytes,
            @Value("${app.visual-search.min-similarity:60}") int minSimilarity) {
        this.maxFileBytes=maxFileBytes;
        this.maxCandidates=Math.max(1,maxCandidates);
        this.maxResults=Math.max(1,maxResults);
        this.timeoutMs=timeoutMs;
        this.maxBytes=maxBytes;
        if(minSimilarity<0||minSimilarity>100)throw new IllegalArgumentException("Visual search min similarity must be between 0 and 100");
        this.minSimilarity=minSimilarity;
    }
    public int maxFileBytes(){return maxFileBytes;}
    public int maxCandidates(){return maxCandidates;}
    public int maxResults(){return maxResults;}
    public int timeoutMs(){return timeoutMs;}
    public int maxBytes(){return maxBytes;}
    public int minSimilarity(){return minSimilarity;}
}
