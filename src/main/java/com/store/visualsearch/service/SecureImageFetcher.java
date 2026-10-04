package com.store.visualsearch.service;

import com.store.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

@Component
public class SecureImageFetcher {
    private static final Set<String> ALLOWED_SCHEMES=Set.of("https");
    private final VisualSearchProperties properties;
    public SecureImageFetcher(VisualSearchProperties properties){this.properties=properties;}

    public byte[] fetch(String rawUrl){
        URI uri=parseAndValidate(rawUrl);
        try{
            for(InetAddress address:InetAddress.getAllByName(uri.getHost())) if(isPrivateOrLocal(address))
                throw new BusinessException("Product image host is not allowed for visual search");
            HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(properties.timeoutMs()); connection.setReadTimeout(properties.timeoutMs());
            connection.setInstanceFollowRedirects(false); connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept","image/avif,image/webp,image/png,image/jpeg;q=0.9,*/*;q=0.1");
            connection.setRequestProperty("User-Agent","ALI-STORE-VisualSearch/1.0");
            try{
                if(connection.getResponseCode()!=HttpURLConnection.HTTP_OK)return null;
                String type=connection.getContentType();
                if(type!=null&&!type.toLowerCase().startsWith("image/"))return null;
                if(connection.getContentLengthLong()>properties.maxBytes())return null;
                try(InputStream input=connection.getInputStream()){return readBounded(input,properties.maxBytes());}
            }finally{connection.disconnect();}
        }catch(BusinessException ex){throw ex;}catch(IOException|RuntimeException ex){return null;}
    }

    private URI parseAndValidate(String rawUrl){
        if(rawUrl==null||rawUrl.isBlank())throw new BusinessException("Product image URL is missing");
        URI uri;
        try{uri=URI.create(rawUrl.trim());}catch(IllegalArgumentException ex){throw new BusinessException("Product image URL is invalid");}
        if(!ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())||uri.getHost()==null||uri.getUserInfo()!=null
                ||(uri.getPort()!=-1&&uri.getPort()!=443))throw new BusinessException("Product image URL must be a public HTTPS URL");
        return uri;
    }

    private byte[] readBounded(InputStream input,int maxBytes)throws IOException{
        try(ByteArrayOutputStream output=new ByteArrayOutputStream(Math.min(maxBytes,64*1024))){
            byte[] buffer=new byte[8192]; int total=0,read;
            while((read=input.read(buffer))!=-1){total+=read;if(total>maxBytes)return null;output.write(buffer,0,read);}
            return output.toByteArray();
        }
    }

    private boolean isPrivateOrLocal(InetAddress address){
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()
                ||address.isSiteLocalAddress()||address.isMulticastAddress())return true;
        byte[] bytes=address.getAddress();
        return bytes.length==16&&(bytes[0]&255)>=252&&(bytes[0]&255)<=253;
    }
}
