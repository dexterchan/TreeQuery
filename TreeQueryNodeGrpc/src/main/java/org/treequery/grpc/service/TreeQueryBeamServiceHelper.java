package org.treequery.grpc.service;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.treequery.beam.cache.BeamCacheOutputBuilder;
import org.treequery.beam.cache.BeamCacheOutputInterface;
import org.treequery.beam.cache.FileBeamCacheOutputImpl;
import org.treequery.beam.cache.RedisCacheOutputImpl;
import org.treequery.config.TreeQuerySetting;
import org.treequery.discoveryservice.DiscoveryServiceInterface;
import org.treequery.exception.CacheNotFoundException;
import org.treequery.exception.TimeOutException;
import org.treequery.utils.AvroIOHelper;
import org.treequery.utils.AvroSchemaHelper;
import org.treequery.model.BasicAvroSchemaHelperImpl;
import org.treequery.model.CacheTypeEnum;
import org.treequery.model.Node;
import org.treequery.proto.TreeQueryRequest;
import org.treequery.service.AsyncTreeQueryClusterService;
import org.treequery.service.StatusTreeQueryCluster;
import org.treequery.service.TreeQueryClusterRunnerImpl;
import org.treequery.service.TreeQueryClusterService;
import org.treequery.utils.AsyncRunHelper;
import org.treequery.utils.JsonInstructionHelper;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

@Slf4j
public class TreeQueryBeamServiceHelper {
    TreeQueryClusterService treeQueryClusterService;
    @NonNull
    BeamCacheOutputBuilder beamCacheOutputBuilder;

    CacheTypeEnum cacheTypeEnum;
    @NonNull
    AvroSchemaHelper avroSchemaHelper;
    @NonNull
    DiscoveryServiceInterface discoveryServiceInterface;
    @NonNull
    TreeQuerySetting treeQuerySetting;

    @Builder
    public TreeQueryBeamServiceHelper(CacheTypeEnum cacheTypeEnum, AvroSchemaHelper avroSchemaHelper, DiscoveryServiceInterface discoveryServiceInterface,TreeQuerySetting treeQuerySetting){
        this.cacheTypeEnum = cacheTypeEnum;
        this.avroSchemaHelper = avroSchemaHelper;
        this.discoveryServiceInterface = discoveryServiceInterface;
        beamCacheOutputBuilder = BeamCacheOutputBuilder.builder()
                                    .cacheTypeEnum(cacheTypeEnum)
                                    .treeQuerySetting(treeQuerySetting).build();
        this.treeQuerySetting = treeQuerySetting;
        init();
    }

    public void init(){
        treeQueryClusterService =  AsyncTreeQueryClusterService.builder()
                .treeQueryClusterRunnerFactory(()->
                        TreeQueryClusterRunnerImpl.builder()
                            .beamCacheOutputBuilder(BeamCacheOutputBuilder.builder()
                                    .cacheTypeEnum(this.cacheTypeEnum)
                                    .treeQuerySetting(this.treeQuerySetting)
                                    .build())
                            .cacheTypeEnum(cacheTypeEnum)
                            .avroSchemaHelper(avroSchemaHelper)
                            .discoveryServiceInterface(discoveryServiceInterface)
                            .build())
                .build();
    }

    public PreprocessInput preprocess(String jsonInput){
        Node rootNode;
        Schema outputSchema;
        try {
            rootNode = JsonInstructionHelper.createNode(jsonInput);
            outputSchema = avroSchemaHelper.getAvroSchema(rootNode);
        }catch(Exception je){
            throw new IllegalArgumentException(String.format("Not able to parse:%s", jsonInput));
        }
        return PreprocessInput.builder()
                .node(rootNode)
                .outputSchema(outputSchema)
                .build();
    }

    public ReturnResult process(TreeQueryRequest.RunMode runMode,
                                PreprocessInput preprocessInput,
                                       boolean renewCache,
                                       long pageSize,
                                       long page,
                                Consumer<GenericRecord> dataConsumer) {

        String identifier = preprocessInput.node.getIdentifier();

        if (!renewCache){
            try{
                Schema schema = AvroIOHelper.getPageRecordFromAvroCache( cacheTypeEnum,
                        treeQuerySetting, identifier, pageSize, page,
                         dataConsumer);
                return ReturnResult.builder()
                        .hashCode(identifier)
                        .statusTreeQueryCluster(
                                StatusTreeQueryCluster.builder()
                                        .status(StatusTreeQueryCluster.QueryTypeEnum.SUCCESS)
                                        .description("Fresh from cache")
                                        .build()
                        )
                        .dataSchema(schema)
                        .build();
            }catch(CacheNotFoundException che){
                log.info(String.format("Cache %s not found, need to rerun", identifier));
            }
        }
        return this.runQuery(preprocessInput.node, pageSize, page, dataConsumer);
    }

    private ReturnResult runQuery(Node rootNode, long pageSize, long page, Consumer<GenericRecord> dataConsumer){
        final AsyncRunHelper asyncRunHelper =  AsyncRunHelper.of(rootNode);
        final String hashCode = rootNode.getIdentifier();
        treeQueryClusterService.runQueryTreeNetwork(rootNode, (status)->{
            log.debug(status.toString());
            asyncRunHelper.continueRun(status);
        });

        try {
            StatusTreeQueryCluster statusTreeQueryCluster = asyncRunHelper.waitFor();
            if(statusTreeQueryCluster.getStatus() != StatusTreeQueryCluster.QueryTypeEnum.SUCCESS){
                return ReturnResult.builder()
                        .hashCode(hashCode)
                        .statusTreeQueryCluster(statusTreeQueryCluster)
                        .build();
            }else{
                try {
                    Schema schema = AvroIOHelper.getPageRecordFromAvroCache(this.cacheTypeEnum,
                            treeQuerySetting,
                            rootNode.getIdentifier(), pageSize, page, dataConsumer);
                    return ReturnResult.builder()
                            .hashCode(hashCode)
                            .statusTreeQueryCluster(statusTreeQueryCluster)
                            .dataSchema(schema)
                            .build();
                }catch(CacheNotFoundException che){
                    log.error(che.getMessage());
                    return ReturnResult.builder()
                            .hashCode(hashCode)
                            .statusTreeQueryCluster(
                                    StatusTreeQueryCluster.builder()
                                    .status(StatusTreeQueryCluster.QueryTypeEnum.SYSTEMERROR)
                                    .description(che.getMessage())
                                    .build()
                            )
                            .build();
                }
            }
        }catch(TimeOutException te){
            log.error(te.getMessage());
            throw new IllegalStateException(String.format("Time out:%s", rootNode.toString()));
        }
    }

    @Builder
    @Getter
    public static class PreprocessInput{
        @NonNull
        private final Node node;
        @NonNull
        private final Schema outputSchema;
    }

    @Builder
    @Getter
    public static class ReturnResult{
        @NonNull
        String hashCode;
        @NonNull
        StatusTreeQueryCluster statusTreeQueryCluster;
        Schema dataSchema;
    }
}
