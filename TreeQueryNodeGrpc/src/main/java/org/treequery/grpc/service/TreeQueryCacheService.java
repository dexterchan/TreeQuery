package org.treequery.grpc.service;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.treequery.service.CacheResult;

import java.util.function.Consumer;

public interface TreeQueryCacheService {
    public CacheResult getPage(String identifier, long pageSize, long page, Consumer<GenericRecord> dataConsumer);
    public void getAsync(String identifier, Consumer<GenericRecord> dataConsumer, Consumer<Throwable> finishCallback);
    public Schema getSchemaOnly(String identifier);
}
