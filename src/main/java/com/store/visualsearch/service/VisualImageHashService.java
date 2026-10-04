package com.store.visualsearch.service;

import com.store.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

@Service
public class VisualImageHashService {
    static final int HASH_SIZE=16;
    private static final int MAX_IMAGE_DIMENSION=4096;

    public long[] hash(byte[] bytes) {
        try { return averageHash(readImage(bytes)); }
        catch(IOException|RuntimeException ex){ throw new BusinessException("The uploaded file is not a valid supported image"); }
    }

    public int similarity(long[] first,long[] second){
        if(first.length!=second.length) throw new IllegalArgumentException("Image hashes must have the same length");
        int different=0,total=first.length*Long.SIZE;
        for(int i=0;i<first.length;i++) different+=Long.bitCount(first[i]^second[i]);
        return Math.max(0,Math.min(100,Math.round((1f-((float)different/total))*100f)));
    }

    private BufferedImage readImage(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length==0)throw new IOException("Empty image");
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            if(input==null)throw new IOException("Unsupported image stream");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Unsupported image format");
            ImageReader reader=readers.next();
            try{
                reader.setInput(input,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||w>MAX_IMAGE_DIMENSION||h>MAX_IMAGE_DIMENSION)throw new IOException("Image dimensions are not supported");
                BufferedImage image=reader.read(0);
                if(image==null)throw new IOException("Image could not be decoded");
                return image;
            }finally{reader.dispose();}
        }
    }

    private long[] averageHash(BufferedImage source){
        BufferedImage normalized=new BufferedImage(HASH_SIZE,HASH_SIZE,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=normalized.createGraphics();
        try{
            g.setComposite(AlphaComposite.Src); g.setColor(Color.WHITE); g.fillRect(0,0,HASH_SIZE,HASH_SIZE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source,0,0,HASH_SIZE,HASH_SIZE,null);
        }finally{g.dispose();}

        long[] hash=new long[4]; int[] gray=new int[HASH_SIZE*HASH_SIZE]; long sum=0;
        for(int y=0;y<HASH_SIZE;y++)for(int x=0;x<HASH_SIZE;x++){
            int rgb=normalized.getRGB(x,y),r=(rgb>>>16)&255,gr=(rgb>>>8)&255,b=rgb&255;
            int value=(299*r+587*gr+114*b)/1000; gray[y*HASH_SIZE+x]=value; sum+=value;
        }
        long average=sum/gray.length,current=0; int bit=0,word=0;
        for(int value:gray){
            current=(current<<1)|(value>=average?1L:0L); bit++;
            if(bit==Long.SIZE){hash[word++]=current;current=0;bit=0;}
        }
        return hash;
    }
}
