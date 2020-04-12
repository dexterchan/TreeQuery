package org.treequery.utils.proxy;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.treequery.cluster.Cluster;
import org.treequery.config.TreeQuerySetting;
import org.treequery.exception.CacheNotFoundException;
import org.treequery.model.CacheTypeEnum;

import java.util.function.Consumer;

public interface TreeQueryClusterAvroCacheInterface {
    //public Schema getPageRecordFromAvroCache(Cluster cluster, CacheTypeEnum cacheTypeEnum, TreeQuerySetting treeQuerySetting, String identifier, long pageSize, long page, Consumer<GenericRecord> dataConsumer) throws CacheNotFoundException ;
}
