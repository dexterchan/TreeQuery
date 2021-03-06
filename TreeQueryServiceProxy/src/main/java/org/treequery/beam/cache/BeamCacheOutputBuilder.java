package org.treequery.beam.cache;

import lombok.Builder;
import org.treequery.config.TreeQuerySetting;
import org.treequery.model.CacheTypeEnum;

import java.util.NoSuchElementException;

@Builder
public class BeamCacheOutputBuilder {
    TreeQuerySetting treeQuerySetting;

    public BeamCacheOutputInterface createBeamCacheOutputImpl(){
        BeamCacheOutputInterface beamCacheOutputInterface;

        switch(treeQuerySetting.getCacheTypeEnum()){
            case FILE:
                beamCacheOutputInterface = new FileBeamCacheOutputImpl(treeQuerySetting.getCacheFilePath());
                break;
            case REDIS:
                beamCacheOutputInterface = new RedisCacheOutputImpl();
                break;
            default:
                throw new NoSuchElementException();
        }

        return beamCacheOutputInterface;
    }
}
