package org.treequery.service;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;

import java.io.Serializable;

@Builder
public class CacheResult implements Serializable {
    @RequiredArgsConstructor
    public enum QueryTypeEnum {
        SUCCESS(0), FAIL(2), RUNNING(1), NOTFOUND(3), SYSTEMERROR(500);
        @Getter
        private final int value;
    }
    @NonNull
    private final String identifier;
    @NonNull
    private final Schema dataSchema;
    @NonNull
    private final String description;

    private final QueryTypeEnum queryTypeEnum;
}
